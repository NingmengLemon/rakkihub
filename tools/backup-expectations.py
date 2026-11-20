#!/usr/bin/env python3
# /// script
# requires-python = ">=3.10"
# dependencies = []
# ///

"""从官方备份里的数据库算出验收用的期望值。

期望值必须独立于我们自己的读取层，否则「读对了」只是自证。这个脚本用 Python 的 sqlite3
直接读库，按 app/schemas 里的列顺序与主键排序，对每张表算一个规范化摘要。

用法：
    python tools/backup-expectations.py \\
        --schema app/schemas/me.rerere.rikkahub.data.db.AppDatabase/32.json \\
        --db /path/to/rikka_hub.db \\
        --out /path/to/backup-expectations.json

规范化规则（Kotlin 侧必须逐字节一致）：
    每个单元格 = 类型字节 + 4 字节小端长度 + 载荷
        类型 'N' NULL（长度 0）
             'I' INTEGER，载荷是十进制 ASCII
             'R' REAL，载荷是 IEEE754 双精度 8 字节大端
             'T' TEXT，载荷是 UTF-8 字节
             'B' BLOB，载荷是原始字节
    行之间写一个 0x1e
    单元格顺序 = schema 里 fields 的顺序；行顺序 = 按 primaryKey 的列升序
"""
import argparse
import hashlib
import json
import re
import sqlite3
import struct
import zipfile

# 附件引用是绝对 file:// 路径，包名与用户号都在里面，导入时会被换成本机路径。校验内容时
# 把这段前缀统一成占位符，否则「路径被正确重写」会被当成「内容不一致」。
FILE_PREFIX = re.compile(r'file:///data/(?:user/\d+|data)/[^/"\s]+/files/')
FILE_PREFIX_PLACEHOLDER = 'file://<files>/'

# 备份里承载附件的四个目录
ATTACHMENT_FOLDERS = ('upload', 'skills', 'fonts', 'media_creation')


def cell(kind: bytes, payload: bytes) -> bytes:
    return kind + struct.pack('<I', len(payload)) + payload


def encode_row(values):
    out = bytearray()
    for value in values:
        if value is None:
            out += cell(b'N', b'')
        elif isinstance(value, int):
            out += cell(b'I', str(value).encode('ascii'))
        elif isinstance(value, float):
            out += cell(b'R', struct.pack('>d', value))
        elif isinstance(value, bytes):
            out += cell(b'B', value)
        else:
            text = FILE_PREFIX.sub(FILE_PREFIX_PLACEHOLDER, str(value))
            out += cell(b'T', text.encode('utf-8'))
    return bytes(out)


def table_digest(cursor, table, columns, primary_key):
    selector = ', '.join(f'"{c}"' for c in columns)
    order = ', '.join(f'"{c}"' for c in primary_key) if primary_key else selector
    cursor.execute(f'SELECT {selector} FROM "{table}" ORDER BY {order}')
    digest = hashlib.sha256()
    count = 0
    first = True
    while True:
        rows = cursor.fetchmany(500)
        if not rows:
            break
        for row in rows:
            if not first:
                digest.update(b'\x1e')
            digest.update(encode_row(row))
            count += 1
            first = False
    return count, digest.hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--schema', required=True)
    parser.add_argument('--db', required=True)
    parser.add_argument('--out', required=True)
    parser.add_argument('--settings', help='解压出来的 settings.json，只统计条数')
    parser.add_argument('--zip', help='完整备份 zip，算附件清单')
    args = parser.parse_args()

    with open(args.schema, encoding='utf-8') as handle:
        schema = json.load(handle)['database']

    connection = sqlite3.connect(f'file:{args.db}?mode=ro', uri=True)
    cursor = connection.cursor()
    version = cursor.execute('PRAGMA user_version').fetchone()[0]
    try:
        identity = cursor.execute('SELECT identity_hash FROM room_master_table').fetchone()[0]
    except sqlite3.Error:
        identity = None

    result = {
        'version': version,
        'schemaFileVersion': schema['version'],
        'identity': identity,
        'tables': {},
    }
    for entity in schema['entities']:
        table = entity['tableName']
        columns = [field['columnName'] for field in entity['fields']]
        primary_key = entity['primaryKey']['columnNames']
        count, digest = table_digest(cursor, table, columns, primary_key)
        result['tables'][table] = {'rows': count, 'sha256': digest}
        print(f'{table}: {count} rows {digest[:16]}...')

    connection.close()

    if args.settings:
        with open(args.settings, encoding='utf-8') as handle:
            settings = json.load(handle)
        assistant_ids = sorted(str(a.get('id')) for a in (settings.get('assistants') or []))
        provider_ids = sorted(str(p.get('id')) for p in (settings.get('providers') or []))
        result['settings'] = {
            'assistants': len(assistant_ids),
            'providers': len(provider_ids),
            'assistantIds': assistant_ids,
            'providerIds': provider_ids,
            'assistantId': settings.get('assistantId'),
            'chatModelId': settings.get('chatModelId'),
        }
        print(f"settings: assistants={result['settings']['assistants']} "
              f"providers={result['settings']['providers']}")

    if args.zip:
        attachments = []
        with zipfile.ZipFile(args.zip) as archive:
            for info in archive.infolist():
                if info.is_dir():
                    continue
                folder = info.filename.split('/')[0]
                if folder not in ATTACHMENT_FOLDERS:
                    continue
                digest = hashlib.sha256()
                with archive.open(info) as stream:
                    while True:
                        chunk = stream.read(1024 * 1024)
                        if not chunk:
                            break
                        digest.update(chunk)
                attachments.append({
                    'path': info.filename,
                    'size': info.file_size,
                    'sha256': digest.hexdigest(),
                })
        result['attachments'] = attachments
        print(f'attachments: {len(attachments)} files')

    with open(args.out, 'w', encoding='utf-8') as handle:
        json.dump(result, handle, ensure_ascii=False, indent=2)
    print(f'wrote {args.out} (user_version={version})')


if __name__ == '__main__':
    main()

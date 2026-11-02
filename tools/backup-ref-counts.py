#!/usr/bin/env python3
"""统计一份 RikkaHub 数据库里的附件引用形状。

设备验收用：恢复之后把库拉下来，数会话、节点，以及消息里的附件引用落在哪种形态。
规则只读 `message_node.messages` 原文，不经过应用代码，所以结果与实现无关。

用法：
    python tools/backup-ref-counts.py --db build/rikka_hub.pulled

拉库之前先 force-stop 应用，否则还在 WAL 里的写入不在主库里；库联同 `-wal`、`-shm`
一起拉下来，脚本读到的是应用停下来的状态。

输出：
    conversations 170
    nodes 676
    relative 2 refs in 2 nodes
    absolute 107 refs in 64 nodes (me.rerere.rikkahub/upload)
    other 0 refs in 0 nodes

形态：
    relative    按相对形态落盘的引用（C36）：`file:upload/<文件名>`
    absolute    `file:///data/(user/<用户号>|data)/<包名>/files/<目录>/...`，按安装与目录分组
    other       其余 `file:` 地址，打印前几条，用来发现没有覆盖到的形态

一条消息节点里可能有多处引用（同一个附件被多条消息用到），所以「处」与「节点」分开数。
"""
import argparse
import re
import sqlite3
import sys
from pathlib import Path

URL = re.compile(r'"url":"(file:[^"]*)"')
ABSOLUTE = re.compile(r'^file:///data/(?:user/\d+|data)/([^/]+)/files/([^/]+)/')


def categorize(url: str) -> tuple[str, str | None]:
    if url.startswith('file:upload/'):
        return 'relative', None
    match = ABSOLUTE.match(url)
    if match:
        return 'absolute', f'{match.group(1)}/{match.group(2)}'
    return 'other', None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--db', required=True, help='拉下来的数据库文件')
    args = parser.parse_args()

    database = Path(args.db)
    if not database.is_file():
        print(f'no such database: {database}', file=sys.stderr)
        return 1

    db = sqlite3.connect(f'{database.resolve().as_uri()}?mode=ro', uri=True)
    try:
        conversations = db.execute('select count(*) from "ConversationEntity"').fetchone()[0]
        rows = db.execute('select messages from message_node').fetchall()
    except sqlite3.Error as error:
        print(f'cannot read the database: {error}', file=sys.stderr)
        return 1
    finally:
        db.close()

    refs: dict[tuple[str, str | None], int] = {}
    nodes: dict[tuple[str, str | None], int] = {}
    other_samples: list[str] = []
    for (messages,) in rows:
        seen = set()
        for url in URL.findall(messages or ''):
            kind, group = categorize(url)
            refs[(kind, group)] = refs.get((kind, group), 0) + 1
            seen.add((kind, group))
            if kind == 'other':
                other_samples.append(url)
        for key in seen:
            nodes[key] = nodes.get(key, 0) + 1

    def line(kind: str, group: str | None) -> str:
        suffix = f' ({group})' if group else ''
        return f'{kind} {refs[(kind, group)]} refs in {nodes[(kind, group)]} nodes{suffix}'

    print(f'conversations {conversations}')
    print(f'nodes {len(rows)}')
    if ('relative', None) in refs:
        print(line('relative', None))
    for group in sorted(group for kind, group in refs if kind == 'absolute'):
        print(line('absolute', group))
    if ('other', None) in refs:
        print(line('other', None))
    for url in other_samples[:5]:
        print(f'  {url}')
    return 0


if __name__ == '__main__':
    sys.exit(main())

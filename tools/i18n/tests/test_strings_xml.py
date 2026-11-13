from __future__ import annotations

from pathlib import Path

from i18n.config import load_config
from i18n.strings_xml import StringsFile

SAMPLE = """<?xml version='1.0' encoding='UTF-8'?>
<resources>
    <!-- <string name="commented">x</string> -->
    <string name="a">A &amp; B</string>
    <string name="b" translatable="false">RikkaHub</string>
    <string-array name="arr">
        <item>one</item>
    </string-array>
    <string name="empty"/>
    <string name="c">Don\\'t</string>
</resources>
"""


def sample(tmp_path: Path, content: str = SAMPLE) -> StringsFile:
    path = tmp_path / "strings.xml"
    path.write_text(content, encoding="utf-8", newline="")
    return StringsFile.load(path)


def test_parses_string_elements_only(tmp_path: Path) -> None:
    file = sample(tmp_path)
    assert list(file.entries().items()) == [
        ("a", "A &amp; B"),
        ("b", "RikkaHub"),
        ("empty", ""),
        ("c", "Don\\'t"),
    ]
    assert list(file.translatable_entries()) == ["a", "empty", "c"]


def test_round_trips_unchanged_content(tmp_path: Path) -> None:
    assert sample(tmp_path).serialize() == SAMPLE


def test_updates_in_place_appends_and_deletes(tmp_path: Path) -> None:
    file = sample(tmp_path)
    file.set("a", "Changed")
    file.set("empty", "Now set")
    file.set("d", "New")
    file.delete("c")
    file.save()
    assert (
        file.path.read_text(encoding="utf-8", newline="")
        == """<?xml version='1.0' encoding='UTF-8'?>
<resources>
    <!-- <string name="commented">x</string> -->
    <string name="a">Changed</string>
    <string name="b" translatable="false">RikkaHub</string>
    <string-array name="arr">
        <item>one</item>
    </string-array>
    <string name="empty">Now set</string>
    <string name="d">New</string>
</resources>
"""
    )


def test_deleting_appended_entries_restores_the_original(tmp_path: Path) -> None:
    file = sample(tmp_path)
    file.set("x", "X")
    file.set("y", "Y")
    appended = sample(tmp_path, file.serialize())
    appended.delete("x")
    appended.delete("y")
    assert appended.serialize() == SAMPLE


def test_creates_a_missing_file(tmp_path: Path) -> None:
    path = tmp_path / "values-xx" / "strings.xml"
    file = StringsFile.load(path)
    assert file.exists is False
    file.set("a", "A")
    file.set("b", "B")
    file.save()
    assert path.read_text(encoding="utf-8", newline="") == (
        "<?xml version='1.0' encoding='UTF-8'?>\n<resources>\n"
        '  <string name="a">A</string>\n'
        '  <string name="b">B</string>\n'
        "</resources>\n"
    )


def test_round_trips_every_strings_xml_in_the_project() -> None:
    config = load_config()
    count = 0
    for module in config.modules:
        root = config.project_root / module.res_path
        for path in sorted(root.glob("values*/strings.xml")):
            assert StringsFile.load(path).serialize() == path.read_text(
                encoding="utf-8", newline=""
            ), path
            count += 1
    assert count > 0

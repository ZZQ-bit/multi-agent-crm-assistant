from pathlib import Path
import re


YAML_PATH = Path("多Agent智能助手 - V3 Stable Enhanced.yml")

REMOVED_IDS = {
    "start-source-attachment_file_state-target",
    "attachment_file_state-source-attachment_image_gate-target",
    "attachment_image_gate-has_images-attachment_image_files-target",
    "attachment_image_gate-false-attachment_image_text-target",
    "attachment_image_files-source-attachment_image_summary-target",
    "attachment_file_state",
    "attachment_image_gate",
    "attachment_image_files",
}

NEW_EDGE = """    - data:
        isInIteration: false
        sourceType: start
        targetType: llm
      id: start-source-attachment_image_summary-target
      source: start
      sourceHandle: source
      target: attachment_image_summary
      targetHandle: target
      type: custom
      zIndex: 0
"""

OLD_CONTEXT_CODE = """def _clean(value):
    if value is None:
        return ''
    if isinstance(value, list):
        return '\\n\\n'.join(_clean(item) for item in value if _clean(item))
    if isinstance(value, dict):
        return '\\n'.join(f'{k}: {_clean(v)}' for k, v in value.items() if _clean(v))
    return str(value).strip()


def _clip(text, limit=4000):
    text = _clean(text)
    if len(text) <= limit:
        return text
    return text[:limit].rstrip() + '\\n...[图片识别内容较长，已截断用于保持上下文可控]'


def main(
    file_count: str = '',
    file_names: str = '',
    image_names: str = '',
    manual_summary: str = '',
    manual_names: str = '',
    image_summary: str = '',
) -> dict:
    names = _clean(image_names) or _clean(file_names) or _clean(manual_names)
    parts = []
    if _clean(manual_summary):
        parts.append('用户/前端提供的附件摘要：\\n' + _clip(manual_summary, 1500))
    if _clean(image_summary):
        title = '图片/截图识别摘要'
        if _clean(image_names):
            title += f'（{_clean(image_names)}）'
        parts.append(title + '：\\n' + _clip(image_summary, 3000))
    summary = '\\n\\n---\\n\\n'.join(parts)
    if not summary and _clean(file_count) not in ['', '0']:
        summary = '本轮检测到图片附件，但没有识别到可用于分析的业务文本。'
    return {
        'attachments_summary': summary,
        'attachment_names': names,
        'image_summary': _clean(image_summary),
    }
"""

NEW_CONTEXT_CODE = """def _clean(value):
    if value is None:
        return ''
    if isinstance(value, list):
        return '\\n\\n'.join(_clean(item) for item in value if _clean(item))
    if isinstance(value, dict):
        return '\\n'.join(f'{k}: {_clean(v)}' for k, v in value.items() if _clean(v))
    return str(value).strip()


def _clip(text, limit=4000):
    text = _clean(text)
    if len(text) <= limit:
        return text
    return text[:limit].rstrip() + '\\n...[图片识别内容较长，已截断用于保持上下文可控]'


def main(
    manual_summary: str = '',
    manual_names: str = '',
    image_summary: str = '',
) -> dict:
    names = _clean(manual_names)
    parts = []
    if _clean(manual_summary):
        parts.append('用户/前端提供的附件摘要：\\n' + _clip(manual_summary, 1500))
    if _clean(image_summary):
        title = '图片/截图识别摘要'
        if names:
            title += f'（{names}）'
        parts.append(title + '：\\n' + _clip(image_summary, 3000))
    summary = '\\n\\n---\\n\\n'.join(parts)
    if not summary and names:
        summary = '本轮检测到图片附件，但没有识别到可用于分析的业务文本。'
    return {
        'attachments_summary': summary,
        'attachment_names': names,
        'image_summary': _clean(image_summary),
    }
"""


def split_blocks(section: str) -> list[str]:
    starts: list[int] = []
    marker = "\n    - data:\n"
    pos = section.find("    - data:\n")
    if pos == -1:
        return [section]
    starts.append(pos)
    search_from = pos + 1
    while True:
        pos = section.find(marker, search_from)
        if pos == -1:
            break
        starts.append(pos + 1)
        search_from = pos + 2

    blocks = [section[:starts[0]]]
    for index, start in enumerate(starts):
        end = starts[index + 1] if index + 1 < len(starts) else len(section)
        blocks.append(section[start:end])
    return blocks


def remove_blocks_by_id(section: str, ids: set[str]) -> str:
    kept: list[str] = []
    for block in split_blocks(section):
        block_ids = []
        for line in block.splitlines():
            stripped = line.strip()
            if stripped.startswith("id: "):
                block_ids.append(stripped.removeprefix("id: ").strip("'\""))
        if any(block_id in ids for block_id in block_ids):
            continue
        kept.append(block)
    return "".join(kept)


def replace_node_block(text: str, node_id: str, transform) -> str:
    marker = f"\n      id: {node_id}\n"
    id_pos = text.find(marker)
    if id_pos == -1:
        raise RuntimeError(f"Node not found: {node_id}")
    start = text.rfind("\n    - data:\n", 0, id_pos)
    if start == -1:
        raise RuntimeError(f"Node block start not found: {node_id}")
    start += 1
    end = text.find("\n    - data:\n", id_pos + 1)
    if end == -1:
        end = len(text)
    block = text[start:end]
    return text[:start] + transform(block) + text[end:]


def update_image_summary_node(block: str) -> str:
    block = block.replace(
        "图片名称：{{#attachment_file_state.image_names#}}。",
        "图片名称：{{#start.attachment_names#}}。",
    )
    block = block.replace(
        "            - attachment_image_files\n            - result",
        "            - sys\n            - files",
    )
    return block


def update_context_node(block: str) -> str:
    import yaml

    parsed = yaml.safe_load(block)
    node = parsed[0] if isinstance(parsed, list) else parsed
    data = node["data"]
    data["code"] = NEW_CONTEXT_CODE
    data["variables"] = [
        {
            "value_selector": ["start", "attachments_summary"],
            "variable": "manual_summary",
        },
        {
            "value_selector": ["start", "attachment_names"],
            "variable": "manual_names",
        },
        {
            "value_selector": ["attachment_image_text", "output"],
            "variable": "image_summary",
        },
    ]
    dumped = yaml.safe_dump([node], allow_unicode=True, sort_keys=False, width=1000)
    lines = dumped.splitlines()
    if not lines or lines[0] != "- data:":
        raise RuntimeError("Failed to dump attachment_context node")
    lines[0] = "    - data:"
    lines[1:] = ["    " + line if line else line for line in lines[1:]]
    return "\n".join(lines) + "\n"


def main() -> None:
    raw = YAML_PATH.read_bytes()
    newline = "\r\n" if b"\r\n" in raw else "\n"
    text = raw.decode("utf-8")
    if newline == "\r\n":
        text = text.replace("\r\n", "\n")

    graph_marker = "  graph:\n    edges:\n"
    nodes_marker = "\n    nodes:\n"
    graph_pos = text.find(graph_marker)
    nodes_pos = text.find(nodes_marker, graph_pos)
    if graph_pos == -1 or nodes_pos == -1:
        raise RuntimeError("Could not locate graph edges/nodes sections")

    before_edges = text[: graph_pos + len(graph_marker)]
    edges_section = text[graph_pos + len(graph_marker):nodes_pos]
    nodes_start = nodes_pos + len(nodes_marker)
    nodes_end_marker = "\n    viewport:"
    nodes_end = text.find(nodes_end_marker, nodes_start)
    if nodes_end == -1:
        raise RuntimeError("Could not locate graph viewport section")
    nodes_section = text[nodes_start:nodes_end]
    after_nodes = text[nodes_end:]

    edges_section = remove_blocks_by_id(edges_section, REMOVED_IDS)
    if "id: start-source-attachment_image_summary-target" not in edges_section:
        edges_section = NEW_EDGE + edges_section

    nodes_section = remove_blocks_by_id(nodes_section, REMOVED_IDS)
    text = before_edges + edges_section + nodes_marker + nodes_section + after_nodes
    text = replace_node_block(text, "attachment_image_summary", update_image_summary_node)
    text = replace_node_block(text, "attachment_context", update_context_node)

    for removed_id in REMOVED_IDS:
        pattern = re.compile(rf"^\s*id:\s*['\"]?{re.escape(removed_id)}['\"]?\s*$", re.MULTILINE)
        if pattern.search(text):
            raise RuntimeError(f"Removed id still present: {removed_id}")
    if "attachment_file_state" in text:
        raise RuntimeError("Removed attachment_file_state reference still present")
    if "attachment_image_files" in text:
        raise RuntimeError("Removed attachment_image_files reference still present")
    if "            - sys\n            - files" not in text:
        raise RuntimeError("Vision node does not read sys.files")

    if newline == "\r\n":
        text = text.replace("\n", "\r\n")
    YAML_PATH.write_bytes(text.encode("utf-8"))


if __name__ == "__main__":
    main()

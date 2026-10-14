"""Engine registry helpers."""

from pathlib import Path
import json


def registered_engines() -> list[str]:
    """Return the engine names in the master registry."""
    index = Path(__file__).with_name("index.json")
    return json.loads(index.read_text(encoding="utf-8"))["engines"]

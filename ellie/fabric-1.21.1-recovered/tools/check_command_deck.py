#!/usr/bin/env python3
"""Check that every native Ellie route has exactly one card in the offline deck."""

from collections import Counter
from html.parser import HTMLParser
from pathlib import Path
import re


PROJECT = Path(__file__).resolve().parents[1]
REGISTRY = PROJECT / "common/src/main/java/com/player2/playerengine/nativecommands/NativeCommandText.java"
DECK = PROJECT / "docs/Ellie_Command_Deck.html"


class CommandDeck(HTMLParser):
    def __init__(self):
        super().__init__()
        self.cards = Counter()
        self.categories = set()
        self.filters = set()

    def handle_starttag(self, tag, attributes):
        attrs = dict(attributes)
        classes = attrs.get("class", "").split()
        if tag == "article" and "cmd-card" in classes:
            card_id = attrs.get("id", "")
            if not card_id.startswith("cmd-") or not attrs.get("data-cat") or not attrs.get("data-search"):
                raise SystemExit(f"Command card needs an ID, category and search text: {card_id!r}")
            self.cards[card_id.removeprefix("cmd-")] += 1
            self.categories.add(attrs["data-cat"])
        if tag == "button" and "filter" in classes:
            self.filters.add(attrs.get("data-category", ""))


def main():
    registry = REGISTRY.read_text(encoding="utf-8")
    match = re.search(r"\bIDS\s*=\s*Set\.of\((.*?)\)\s*;", registry, re.DOTALL)
    if not match:
        raise SystemExit("Cannot find the native command registry in NativeCommandText.java")
    routes = set(re.findall(r'"([a-z0-9_]+)"', match[1]))
    if not routes:
        raise SystemExit("The native command registry has no readable IDs")

    deck = CommandDeck()
    deck.feed(DECK.read_text(encoding="utf-8"))
    deck.close()
    problems = {
        "Missing command cards": routes - deck.cards.keys(),
        "Cards for unknown commands": deck.cards.keys() - routes,
        "Duplicate cards": {name for name, count in deck.cards.items() if count > 1},
        "Card categories without filters": (deck.categories | {"all"}) - deck.filters,
        "Filters without cards": deck.filters - (deck.categories | {"all"}),
    }
    if any(problems.values()):
        raise SystemExit("\n".join(f"{label}: {', '.join(sorted(values))}" for label, values in problems.items() if values))
    print(f"Command deck matches all {len(routes)} native Ellie routes.")


if __name__ == "__main__":
    main()

# Ellie command deck

When adding, removing, renaming, or changing the syntax of a native Ellie
command, update `docs/Ellie_Command_Deck.html` in the same change. Use the
implemented route to verify syntax, examples, and prerequisites. Give new
commands a searchable card and include relevant subcommands on that card.
Keep the `/ellie` argument suggestions in sync with the native command syntax.
Run `python tools/check_command_deck.py` from this directory.

The repository copy is the source for the offline deck. When working on the
owner's PC, refresh the Downloads copy they use if it is available.

# Rebuilding conditions.json from the real condition effects

Done on 2026-11-12 (LLS-8) for Andrzej's installed mods. Run again when the mod list changes.

1. Unzip the game's `starsector-core/starfarer.api.zip` into `api/` here (vanilla condition sources).
2. `python -I index_conditions.py cond_index.json`: every lived-in condition of vanilla and the enabled mods,
   with its effect class and where its code is (source, or a jar).
3. `python -I bundle_conditions.py`: one file per condition in `bundles/` (description, hazard, code or javap).
   `bundles/` and `api/` hold third-party code: never commit them (gitignored).
4. Split the bundles into batches and have readers follow `READER_BRIEF.md`, writing `reader*.jsonl`.
   `reader_all.jsonl` holds the 2026-11-12 findings for all 283 conditions.
5. Review, put corrections in `overrides.json` (values, and owners for faction-only effects), then
   `python -I merge_readers.py --table` to review and `--write` to rebuild `data/config/landlordsector/conditions.json`.

The scripts take paths relative to this folder except the game and mods folders at the top of each.

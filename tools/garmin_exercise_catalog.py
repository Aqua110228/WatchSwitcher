#!/usr/bin/env python3
"""Build the Garmin exercise catalog from the FIT profile and the picker name lists.

Usage:
    tools/garmin_exercise_catalog.py [-p PROFILE] [-l LISTS] [-o OUTPUT] [--check]

This takes:
* The (exercise_category, exercise_name) pairs from FIT profile (fit_profile.json)
* The name lists per muscle group / equipment (app/src/test/resources/garmin/exercises/*)
* A list of name overrides (app/src/test/resources/garmin/exercises/overrides.json)

Each name in the list is matched against the pairs from the FIT profile, or an override.
"""

import argparse
import glob
import json
import os
import re
import sys
import unicodedata
from collections import defaultdict

PROFILE = "FitCodeGenerator/src/main/resources/fit_profile.json"
LISTS = "app/src/test/resources/garmin/exercises"
OUTPUT = "app/src/main/assets/workouts/exercises/garmin.json"

SPORTS = [
    "strength",
    "yoga",
    "pilates",
    "mobility"
]

# filter id -> (folder under each sport, filter name)
FILTERS = {
    "muscle": ("muscle_groups", "Muscle group"),
    "equipment": ("equipment", "Equipment"),
}

# Filter value names that are not a plain title case of their id
VALUE_NAMES = {
    "ez_bar": "EZ Bar",
    "pull_up_bar": "Pull-up Bar",
    "trx": "TRX",
}

# Categories whose exercises the picker shows with the category as a prefix or
# suffix, e.g. BANDED_EXERCISES/ROW is "Banded Row". A bare "Row" is never one of these.
AFFIXED = {
    "CATEGORY_BANDED_EXERCISES": "banded",
    "CATEGORY_SUSPENSION": "suspension",
    "CATEGORY_SANDBAG": "sandbag",
    "CATEGORY_BATTLE_ROPE": "battle rope",
    "CATEGORY_SLEDGE_HAMMER": "sledge hammer",
    "CATEGORY_SLED": "sled",
    "CATEGORY_TIRE": "tire",
    "CATEGORY_LADDER": "ladder",
}

# The categories the yoga, pilates and mobility lists are made of
FLEXIBILITY = {
    "CATEGORY_POSE",
    "CATEGORY_MOVE",
    "CATEGORY_WARM_UP"
}


def norm(text):
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode()
    text = text.lower().replace("&", "and")
    text = text.replace("bosu balance trainer", "bosu ball")
    return re.sub(r"[^a-z0-9]", "", text)


def title(key):
    return " ".join(w.capitalize() for w in key.lower().split("_"))


def read_names(path):
    with open(path, encoding="utf-8") as f:
        return {line.strip() for line in f if line.strip()}


def read_profile(path):
    """
    Returns [(category, key, code)]. The key and the code are None for a category's
    own generic exercise, which is written with exercise_name unset.
    """
    with open(path) as f:
        enums = {e["name"]: e["entries"] for e in json.load(f)["enumerations"]}
    entries = []
    for category in enums["ExerciseCategory"]:
        name = category["name"]
        if name == "CATEGORY_UNKNOWN":
            continue
        enum = "".join(w.capitalize() for w in name.removeprefix("CATEGORY_").split("_")) + "ExerciseName"
        entries.append((name, None, None))
        for entry in enums.get(enum, []):
            entries.append((name, entry["name"], entry["num"]))
    return entries


def entry_forms(category, key):
    """
    The texts the picker might show for one enum entry, as {text: rank}. Rank 0 is
    the key itself, rank 1 a rewording. A name that matches several entries takes
    the lowest rank.
    """
    if key is None:
        text = title(category.removeprefix("CATEGORY_"))
        return {text.replace("Flye", "Fly"): 1} | {text: 0}
    text = key.lower().replace("_", " ")
    out = {text.replace("flye", "fly"): 1, text.replace("dumbell", "dumbbell"): 1} | {text: 0}
    if category in AFFIXED:
        affix = AFFIXED[category]
        out = {affix + " " + f: r for f, r in out.items()} | {f + " " + affix: 1 for f in out}
        # SLEDGE_HAMMER/HAMMER_SLAM is shown as "Sledge Hammer Slam"
        last = affix.split()[-1]
        if text.startswith(last + " "):
            out[affix + text[len(last):]] = 1
    if category == "CATEGORY_POSE":
        out |= {f + " pose": 1 for f in list(out)}
    m = re.match(r"stretch (.*)", text)
    if m:
        out[m.group(1) + " stretch"] = 1
    return out


def name_forms(name):
    """
    The rewordings of one picker name that are tried after the name itself.
    """
    m = re.match(r"(.*) \(with weights?\)$", name)
    if m:
        return {"weighted " + m.group(1)}
    return {re.sub(r" \([^)]*\)", "", name)} - {name}


def resolve(name, lists, by_form):
    """
    The one enum entry for a picker name, or the set of candidates when there is
    not exactly one.
    """
    ranked = dict(by_form.get(norm(name), {}))
    for form in name_forms(name):
        for entry, rank in by_form.get(norm(form), {}).items():
            ranked[entry] = min(rank + 1, ranked.get(entry, rank + 1))
    candidates = set(ranked)
    if len(candidates) > 1:
        best = min(ranked.values())
        candidates = {c for c in candidates if ranked[c] == best}
    # A category's own row is its same-named key when the category has one
    for generic in [c for c in candidates if c[1] is None]:
        keyed = {c for c in candidates if c[1] is not None and c[0] == generic[0]}
        if keyed:
            candidates = keyed
    if len(candidates) > 1:
        in_strength = "strength" in lists
        preferred = {c for c in candidates if (c[0] in FLEXIBILITY) != in_strength}
        if len(preferred) == 1:
            candidates = preferred
    return next(iter(candidates)) if len(candidates) == 1 else candidates


def build(profile, lists):
    entries = read_profile(profile)
    by_form = defaultdict(dict)  # normalized text -> {entry: rank}
    for entry in entries:
        for form, rank in entry_forms(entry[0], entry[1]).items():
            key = norm(form)
            by_form[key][entry] = min(rank, by_form[key].get(entry, rank))
    by_ref = {f"{c}/{k}" if k else c: (c, k, n) for c, k, n in entries}

    with open(os.path.join(lists, "overrides.json")) as f:
        overrides = json.load(f)

    list_names = {sport: read_names(os.path.join(lists, sport, "all.txt")) for sport in SPORTS}
    values = {filter_id: set() for filter_id in FILTERS}  # filter id -> value ids
    tags = defaultdict(lambda: defaultdict(set))  # name -> filter id -> value ids
    for sport in SPORTS:
        for filter_id, (folder, _) in FILTERS.items():
            for path in sorted(glob.glob(os.path.join(lists, sport, folder, "*.txt"))):
                value_id = os.path.basename(path).removesuffix(".txt").replace("-", "_")
                values[filter_id].add(value_id)
                for name in read_names(path):
                    tags[name][filter_id].add(value_id)

    # The picker shows a few names twice, in different case. Keep one.
    display = {}
    for names in list_names.values():
        for name in names:
            key = norm(name)
            if key not in display or (display[key].isupper() and not name.isupper()):
                display[key] = name
    lists_of = defaultdict(set)
    for list_id, names in list_names.items():
        for name in names:
            lists_of[display[norm(name)]].add(list_id)
    tags_of = defaultdict(lambda: defaultdict(set))
    for name, by_filter in tags.items():
        for filter_id, value_ids in by_filter.items():
            tags_of[display[norm(name)]][filter_id] |= value_ids

    problems = []
    exercises = {}
    for name in sorted(lists_of, key=str.lower):
        # (category, key, code) -> the sport lists that show it under this name
        resolved_lists = defaultdict(set)
        if name in overrides:
            override = overrides[name]
            if override is None:
                continue
            refs = override if isinstance(override, dict) else {list_id: override for list_id in lists_of[name]}
            missing = lists_of[name] - refs.keys()
            unknown = {ref for list_id, ref in refs.items() if list_id in lists_of[name] and ref not in by_ref}
            if missing:
                problems.append(f"{name!r}: override has no pair for {', '.join(sorted(missing))}")
                continue
            if unknown:
                problems.append(f"{name!r}: override {', '.join(sorted(unknown))} is not in the FIT profile")
                continue
            for list_id in lists_of[name]:
                resolved_lists[by_ref[refs[list_id]]].add(list_id)
        else:
            resolved = resolve(name, lists_of[name], by_form)
            if isinstance(resolved, set):
                what = "no match" if not resolved else "ambiguous: " + ", ".join(
                    sorted(f"{c}/{k}" if k else c for c, k, _ in resolved))
                problems.append(f"{name!r}: {what}")
                continue
            resolved_lists[resolved] = lists_of[name]
        for resolved, list_ids in resolved_lists.items():
            if resolved in exercises:
                problems.append(f"{name!r} and {exercises[resolved]['name']!r} both resolve to {resolved}")
                continue
            category, key, code = resolved
            short = category.removeprefix("CATEGORY_").lower()
            exercises[resolved] = {
                "id": f"{short}/{key.lower().lstrip('_')}" if key else short,
                "name": name,
                "lists": sorted(list_ids),
                "filters": {filter_id: sorted(tags_of[name][filter_id]) for filter_id in FILTERS if tags_of[name][filter_id]},
                "category": category,
                "code": code,
            }

    if problems:
        print("Cannot build the catalog:", file=sys.stderr)
        for problem in problems:
            print("  " + problem, file=sys.stderr)
        sys.exit(1)

    return {
        "filters": [
            {
                "id": filter_id,
                "name": name,
                "values": [{"id": v, "name": VALUE_NAMES.get(v, title(v))} for v in sorted(values[filter_id])],
            }
            for filter_id, (_, name) in FILTERS.items()
        ],
        "exercises": sorted(exercises.values(), key=lambda e: e["name"].lower()),
    }


def render(catalog):
    filters = json.dumps(catalog["filters"], ensure_ascii=False, indent=2).replace("\n", "\n  ")
    exercises = ",\n".join("    " + json.dumps(e, ensure_ascii=False) for e in catalog["exercises"])
    return '{\n  "filters": ' + filters + ',\n  "exercises": [\n' + exercises + "\n  ]\n}\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("-p", "--profile", default=PROFILE)
    parser.add_argument("-l", "--lists", default=LISTS)
    parser.add_argument("-o", "--output", default=OUTPUT)
    parser.add_argument("--check", action="store_true", help="Only check that the output is up to date")
    args = parser.parse_args()

    catalog = build(args.profile, args.lists)
    text = render(catalog)

    if args.check:
        with open(args.output, encoding="utf-8") as f:
            if f.read() != text:
                print("%s is out of date" % args.output, file=sys.stderr)
                return 1
        return 0

    with open(args.output, "w", encoding="utf-8") as f:
        f.write(text)
    print("Wrote %s: %d exercises" % (args.output, len(catalog["exercises"])))
    for sport in SPORTS:
        print("  %s: %d" % (sport, sum(sport in e["lists"] for e in catalog["exercises"])))
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Build the Zepp OS exercise catalog from captured training-template JSON files.

Usage:
    tools/zeppos_exercise_catalog.py [-c CAPTURES] [-o OUTPUT] [--check]
"""

import argparse
import collections
import json
import pathlib
import re
import sys

CAPTURES = "app/src/test/resources/zepp_os"
OUTPUT = "app/src/main/assets/workouts/exercises/zeppos.json"

# Position code -> muscle. The watch reports a left and a right code per muscle; both
# map to the same entry, since the catalog filters by muscle and not by side.
# Matched against the body diagram the Zepp app draws for each exercise.
#
# The names are simplified to the common gym terms.
MUSCLES = {
    (1, 2): "Abs",            # 1, 2 | Rectus abdominis | 1=R 2=L
    (3, 4): "Front delts",    # 3, 4 | Deltoid, anterior | 3=R 4=L
    (5, 6): "Chest",          # 5, 6 | Pectorals | 5=R 6=L
    (7, 8): "Obliques",       # 7, 8 | External obliques | 7=R 8=L
    (10, 11): "Biceps",       # 10, 11 | Biceps | 11=L
    (12, 13): "Forearms",     # 12, 13 | Forearm flexors
    (14, 15): "Quads",        # 14, 15 | Quadriceps | 14=R 15=L
    (16, 17): "Calves",       # 16, 17 | Calf, posterior
    (19, 20): "Lats",         # 19, 20 | Latissimus dorsi | 19=L 20=R
    (22, 23): "Triceps",      # 22, 23 | Triceps | 22=L 23=R
    (24, 25): "Forearms",     # 24, 25 | Forearm extensors
    (26, 27): "Glutes",       # 26, 27 | Glutes | 26=L 27=R
    (28, 29): "Hamstrings",   # 28, 29 | Hamstrings | 28=L 29=R
    (30, 31): "Shins",        # 30, 31 | Tibialis anterior
    (32, 33): "Hip flexors",  # 32, 33 | Iliopsoas | 32=R 33=L
    (34, 35): "Upper back",   # 34, 35 | Upper back | 34=L 35=R
    (36, 37): "Lower back",   # 36, 37 | Lower back | 36=L 37=R
    (38, 39): "Rear delts",   # 38, 39 | Deltoid, posterior | 38=L 39=R
    (40, 41): "Neck",         # 40, 41 | Neck
    (42, 43): "Inner thighs", # 42, 43 | Adductors | 42=L 43=R
    (44, 45): "Outer hips",   # 44, 45 | IT band / lateral hip | 44=R 45=L
                              # 46, 47 | unknown | 46=L 47=R
    (48, 49): "Lower abs",    # 48, 49 | Lower abdominals | 48=R 49=L
}

# Codes that appear in the captures but whose muscle is not identified.
UNKNOWN_CODES = {46, 47}

# The filter value for an exercise with no position at all, so that it is still
# reachable when a filter is applied.
OTHER_MUSCLE = "Others"

# Which catalog list an exercise goes in, by the sportType of the capture it came from.
SPORT_LISTS = {
    52: "strength"
}

FILTER_ID = "muscle"
FILTER_NAME = "Muscle group"

def slug(text):
    return re.sub(r"_+", "_", re.sub(r"[^a-z0-9]+", "_", text.lower())).strip("_")


def read_exercises(captures):
    """actionType -> {name, main, sub}, merged over every capture."""
    found = {}
    unknown_sports = set()
    for path in sorted(pathlib.Path(captures).glob("*.json")):
        template = json.loads(path.read_text(encoding="utf-8"))
        for interval in template.get("intervals", []):
            action_type = interval.get("actionType")
            if not action_type:
                continue
            entry = found.setdefault(
                action_type,
                {"name": interval["actionName"], "main": set(), "sub": set(), "lists": set()},
            )
            if entry["name"] != interval["actionName"]:
                print(
                    "WARNING: action type %d has multiple names: %r and %r"
                    % (action_type, entry["name"], interval["actionName"]),
                    file=sys.stderr,
                )
            entry["main"].update(interval.get("mainPositions", []))
            entry["sub"].update(interval.get("subPositions", []))
            sport_type = template.get("sportType")
            if sport_type in SPORT_LISTS:
                entry["lists"].add(SPORT_LISTS[sport_type])
            else:
                unknown_sports.add(sport_type)
    if unknown_sports:
        print(
            "WARNING: no list for sportType %s"
            % sorted(unknown_sports),
            file=sys.stderr,
        )
    return found


def muscle_of(code, reported):
    for codes, name in MUSCLES.items():
        if code in codes:
            return name
    if code not in UNKNOWN_CODES:
        reported.add(code)
    return None


def build(found):
    reported_unmapped = set()
    used_muscles = set()
    used_other = False
    exercises = []
    ids = {}

    # IDs that matches more than one human-readable action.
    duplicated = {
        exercise_id
        for exercise_id, count in collections.Counter(
            slug(e["name"]) for e in found.values()
        ).items()
        if count > 1
    }

    for action_type in sorted(found):
        entry = found[action_type]
        muscles = set()
        for code in sorted(entry["main"] | entry["sub"]):
            name = muscle_of(code, reported_unmapped)
            if name:
                muscles.add(name)
        used_muscles.update(muscles)
        if not muscles:
            used_other = True

        is_duplicate = slug(entry["name"]) in duplicated
        name = "%s (%d)" % (entry["name"], action_type) if is_duplicate else entry["name"]

        exercise_id = slug(entry["name"])
        if is_duplicate:
            exercise_id = "%s_%d" % (exercise_id, action_type)
        if exercise_id in ids:
            print(
                "WARNING: id %r is both action type %d and %d"
                % (exercise_id, ids[exercise_id], action_type),
                file=sys.stderr,
            )
        ids[exercise_id] = action_type

        values = sorted(slug(m) for m in muscles) if muscles else [slug(OTHER_MUSCLE)]
        exercises.append(
            {
                "id": exercise_id,
                "name": name,
                "lists": sorted(entry["lists"]),
                "filters": {FILTER_ID: values},
                "actionType": action_type,
                "actionName": entry["name"],
                "mainPositions": sorted(entry["main"]),
                "subPositions": sorted(entry["sub"]),
            }
        )

    if duplicated:
        print("%d name(s) used by more than one action type:" % len(duplicated), file=sys.stderr)
        for exercise_id in sorted(duplicated):
            group = sorted(
                (t, e["name"]) for t, e in found.items() if slug(e["name"]) == exercise_id
            )
            print("  %s" % ", ".join("%s (%d)" % (n, t) for t, n in group), file=sys.stderr)

    if reported_unmapped:
        print(
            "WARNING: no muscle for position code(s) %s" % sorted(reported_unmapped),
            file=sys.stderr,
        )

    values = []
    for name in MUSCLES.values():
        if name in used_muscles and not any(v["name"] == name for v in values):
            values.append({"id": slug(name), "name": name})
    if used_other:
        values.append({"id": slug(OTHER_MUSCLE), "name": OTHER_MUSCLE})

    exercises.sort(key=lambda e: (e["name"].lower(), e["actionType"]))
    return {
        "filters": [{"id": FILTER_ID, "name": FILTER_NAME, "values": values}],
        "exercises": exercises,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("-c", "--captures", default=CAPTURES)
    parser.add_argument("-o", "--output", default=OUTPUT)
    parser.add_argument(
        "--check",
        action="store_true",
        help="report what would change instead of writing the file",
    )
    args = parser.parse_args()

    found = read_exercises(args.captures)
    if not found:
        print("No exercises in %s" % args.captures, file=sys.stderr)
        return 1

    catalog = build(found)
    text = json.dumps(catalog, indent=2, ensure_ascii=False) + "\n"

    output = pathlib.Path(args.output)
    before = json.loads(output.read_text(encoding="utf-8")) if output.exists() else {"exercises": []}
    old_ids = {e["id"] for e in before["exercises"]}
    new_ids = {e["id"] for e in catalog["exercises"]}

    print("%d exercises, %d muscles" % (len(catalog["exercises"]), len(catalog["filters"][0]["values"])))
    if old_ids - new_ids:
        print("WARNING: id(s) no longer in the catalog: %s" % sorted(old_ids - new_ids), file=sys.stderr)
    print("%d added, %d unchanged" % (len(new_ids - old_ids), len(new_ids & old_ids)))

    if args.check:
        return 0

    output.write_text(text, encoding="utf-8")
    print("Wrote %s" % output)
    return 0


if __name__ == "__main__":
    sys.exit(main())

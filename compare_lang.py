import yaml
paths = ["main/resources/lang/en_us.yml","main/resources/lang/zh_tw.yml","main/resources/lang/zh_cn.yml"]

data = {p: yaml.safe_load(open(p, encoding="utf-8")) for p in paths}

def flatten(d, prefix=""):
    if isinstance(d, dict):
        for k, v in d.items():
            yield from flatten(v, f"{prefix}.{k}" if prefix else k)
    else:
        yield prefix

keys = {p: set(flatten(data[p])) for p in paths}
base = keys[paths[0]]
for p in paths[1:]:
    missing = sorted(base - keys[p])
    extra = sorted(keys[p] - base)
    print(f"\n=== {p}")
    print("missing vs en_us:")
    for k in missing:
        print("  -", k)
    print("total missing:", len(missing))
    print("extra vs en_us:")
    for k in extra:
        print("  +", k)
    print("total extra:", len(extra))

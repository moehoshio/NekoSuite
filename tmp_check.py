import yaml

data = yaml.safe_load(open('"'"'main/resources/lang/en_us.yml'"'"', encoding='"'"'utf-8'"'"'))

def flat(x, p='"'"''"'"'):
    if isinstance(x, dict):
        for k, v in x.items():
            yield from flat(v, f"{p}.{k}" if p else k)
    else:
        yield p

keys = set(flat(data))
for key in ['"'"'menu.arena.title'"'"', '"'"'menu.arena.start_button'"'"', '"'"'menu.wish.title'"'"', '"'"'menu.invbackup.title'"'"']:
    print(key, key in keys)
print('"'"'key count'"'"', len(keys))

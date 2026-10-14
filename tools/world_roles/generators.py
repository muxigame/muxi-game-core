"""Persist saved stems as an enabled datapack: custom dimensions otherwise reload from mod JSON."""
from collections import OrderedDict
import json
from .nbt import Tag,python_value
from .transform import HOME,SURVIVAL
PACK='muxi-world-roles';PACK_ID='file/'+PACK
DATA_PACK_FORMAT=48 # Pinned Minecraft 1.21.1 server version.json pack_version.data.

def codec_json(tag):
    if tag.kind==1:return bool(tag.value) # Generator codecs encode booleans as NBT bytes.
    if tag.kind==9:return [codec_json(t) for t in tag.value[1]]
    if tag.kind==10:return {k:codec_json(v) for k,v in tag.value.items()}
    return python_value(tag)

def preserve_generators(level):
    data=level.value['Data'].value;dims=data['WorldGenSettings'].value['dimensions'].value
    files={'datapacks/'+PACK+'/pack.mcmeta':{'pack':{'pack_format':DATA_PACK_FORMAT,'description':'Retained original home and survival generators after offline world role migration'}}}
    for dimension in (HOME,SURVIVAL):
        stem=dims[dimension]
        generator=stem.value['generator'].value['type'].value
        if generator not in ('minecraft:noise','minecraft:flat','minecraft:debug'):raise ValueError('Unaudited saved generator codec: '+generator)
        namespace,name=dimension.split(':',1)
        files['datapacks/'+PACK+'/data/'+namespace+'/dimension/'+name+'.json']=codec_json(stem)
    packs=data.get('DataPacks',Tag(10,OrderedDict()))
    values=OrderedDict(packs.value)
    enabled=list(values.get('Enabled',Tag(9,(8,[Tag(8,'vanilla')]))).value[1])
    if not any(t.value==PACK_ID for t in enabled):enabled.append(Tag(8,PACK_ID))
    values['Enabled']=Tag(9,(8,enabled))
    disabled=list(values.get('Disabled',Tag(9,(8,[]))).value[1])
    values['Disabled']=Tag(9,(8,[t for t in disabled if t.value!=PACK_ID]))
    data['DataPacks']=Tag(10,values)
    return {p:(json.dumps(v,ensure_ascii=False,indent=2)+'\n').encode('utf-8') for p,v in files.items()}

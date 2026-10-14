"""Semantic, simultaneous role remapping. Generator/type resource IDs are not dimension references."""
import re
from collections import OrderedDict
from .nbt import Tag
OLD_HOME='minecraft:overworld';OLD_SURVIVAL='muxi_game_core:overworld';HOME='muxi_game_core:homeworld';SURVIVAL=OLD_HOME
MAPPING={OLD_HOME:HOME,OLD_SURVIVAL:SURVIVAL}
RESOURCE_FIELDS={'type','settings','preset','dimension_type','generator','noise_settings'}
PORTAL=re.compile(r'^(minecraft:overworld|muxi_game_core:overworld)/(-?\d+)/([xz])$')

def reference(value):
    if value in MAPPING:return MAPPING[value]
    found=PORTAL.fullmatch(value)
    return MAPPING[found[1]]+'/'+found[2]+'/'+found[3] if found else value

def remap_tag(tag,path=(),changes=None):
    changes=[] if changes is None else changes
    if tag.kind==8:
        if path and path[-1] in RESOURCE_FIELDS:return tag
        new=reference(tag.value)
        if new!=tag.value:changes.append('/'.join(path));return Tag(8,new)
    elif tag.kind==9:
        return Tag(9,(tag.value[0],[remap_tag(x,path+(str(i),),changes) for i,x in enumerate(tag.value[1])]))
    elif tag.kind==10:
        fields=OrderedDict()
        for key,value in tag.value.items():
            # Registries/generators are transformed only by the explicit level.dat role swap.
            if key in ('WorldGenSettings','DataPacks','enabled_features'):fields[key]=value;continue
            new=reference(key)
            if new in fields:raise ValueError('Dimension key collision at '+ '/'.join(path+(new,)))
            if new!=key:changes.append('/'.join(path+(key,)))
            fields[new]=remap_tag(value,path+(key,),changes)
        return Tag(10,fields)
    return tag

def remap_json(value,path=(),changes=None):
    changes=[] if changes is None else changes
    if isinstance(value,str):
        if path and path[-1] in RESOURCE_FIELDS:return value
        new=reference(value)
        if new!=value:changes.append('/'.join(path))
        return new
    if isinstance(value,list):return [remap_json(v,path+(str(i),),changes) for i,v in enumerate(value)]
    if isinstance(value,dict):
        out={}
        for key,v in value.items():
            new=reference(key)
            if new in out:raise ValueError('Dimension JSON key collision')
            if new!=key:changes.append('/'.join(path+(key,)))
            out[new]=remap_json(v,path+(key,),changes)
        return out
    return value

def player_defaults(tag):
    if tag.kind!=10:return tag
    fields=OrderedDict(tag.value)
    if 'Dimension' not in fields:fields['Dimension']=Tag(8,HOME)
    if all(k in fields for k in ('SpawnX','SpawnY','SpawnZ')) and 'SpawnDimension' not in fields:fields['SpawnDimension']=Tag(8,HOME)
    return Tag(10,fields)

def level_roles(tag,main_spawn,hub):
    if tag.kind!=10 or 'Data' not in tag.value:raise ValueError('Missing level.dat Data')
    data=tag.value['Data'];dims=data.value['WorldGenSettings'].value['dimensions'];old=dims.value
    if HOME in old:raise ValueError('Target homeworld is already registered: existing save needs explicit conflict resolution')
    if OLD_HOME not in old or OLD_SURVIVAL not in old:raise ValueError('Both original world generators are required')
    changed=remap_tag(tag);new_dims=OrderedDict()
    for key,value in old.items():new_dims[MAPPING.get(key,key)]=value
    changed.value['Data'].value['WorldGenSettings']=Tag(10,OrderedDict(data.value['WorldGenSettings'].value))
    changed.value['Data'].value['WorldGenSettings'].value['dimensions']=Tag(10,new_dims)
    old_spawn={name:data.value.get('Spawn'+name,Tag(3,0)) for name in ['X','Y','Z']}
    role={'schema':Tag(3,2),'portalHub':Tag(8,hub),'homeSpawn':Tag(10,OrderedDict((name.lower(),v) for name,v in old_spawn.items()))}
    role['homeSpawn'].value['angle']=data.value.get('SpawnAngle',Tag(5,0.0))
    if main_spawn is not None:
        for name,value in zip(['SpawnX','SpawnY','SpawnZ'],main_spawn):changed.value['Data'].value[name]=Tag(3,value)
    if 'Player' in changed.value['Data'].value:changed.value['Data'].value['Player']=player_defaults(changed.value['Data'].value['Player'])
    return changed,Tag(10,OrderedDict([('data',Tag(10,OrderedDict(role)))]))

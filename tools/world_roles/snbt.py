"""SNBT structural rewrite preserving formatting, numeric suffixes and opaque untouched literals."""
from .transform import reference, RESOURCE_FIELDS

def rewrite(text):
    index=0;edits=[];changes=[]
    def ws():
        nonlocal index
        while index<len(text) and text[index].isspace():index+=1
    def string(key=False):
        nonlocal index
        ws();start=index
        if index>=len(text):raise ValueError('Truncated SNBT')
        quote=text[index] if text[index] in "\"'" else None
        if quote:
            index+=1;out=[]
            while index<len(text):
                c=text[index];index+=1
                if c==quote:return ''.join(out),start,index,quote
                if c=='\\':
                    if index>=len(text) or text[index] not in ('\\',quote):raise ValueError('Invalid SNBT string escape')
                    c=text[index];index+=1
                out.append(c)
            raise ValueError('Unclosed SNBT string')
        stop=',}] \t\r\n'+(':' if key else '')
        while index<len(text) and text[index] not in stop:index+=1
        if index==start:raise ValueError('Empty SNBT token')
        return text[start:index],start,index,None
    def replace(token,new,path):
        value,start,end,quote=token
        if new!=value:
            rendered=quote+new.replace('\\','\\\\').replace(quote,'\\'+quote)+quote if quote else new
            edits.append((start,end,rendered));changes.append('/'.join(path))
    def node(path=(),protected=False,depth=0):
        nonlocal index
        if depth>512:raise ValueError('SNBT nesting limit')
        ws()
        if index>=len(text):raise ValueError('Missing SNBT value')
        c=text[index]
        if c=='{':
            index+=1;seen=set();ws()
            if index<len(text) and text[index]=='}':index+=1;return
            while True:
                key=string(True);ws()
                if index>=len(text) or text[index]!=':':raise ValueError('SNBT compound missing colon')
                index+=1;new=key[0] if protected else reference(key[0])
                if new in seen:raise ValueError('SNBT dimension key collision')
                seen.add(new);replace(key,new,path+(key[0],));node(path+(key[0],),protected or key[0] in ('WorldGenSettings','DataPacks'),depth+1);ws()
                if index<len(text) and text[index]=='}':index+=1;return
                if index>=len(text) or text[index]!=',':raise ValueError('SNBT compound missing comma')
                index+=1
        elif c=='[':
            index+=1;ws()
            if index+1<len(text) and text[index] in 'BIL' and text[index+1]==';':index+=2
            ws();child=0
            if index<len(text) and text[index]==']':index+=1;return
            while True:
                node(path+(str(child),),protected,depth+1);child+=1;ws()
                if index<len(text) and text[index]==']':index+=1;return
                if index>=len(text) or text[index]!=',':raise ValueError('SNBT list missing comma')
                index+=1
        else:
            token=string();new=token[0] if protected or (path and path[-1] in RESOURCE_FIELDS) else reference(token[0]);replace(token,new,path)
    node();ws()
    if index!=len(text):raise ValueError('Trailing SNBT content')
    for start,end,value in sorted(edits,reverse=True):text=text[:start]+value+text[end:]
    return text,changes

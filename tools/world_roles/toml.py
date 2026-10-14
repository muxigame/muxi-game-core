"""TOML string rewrite, preserving comments and layout; stdlib parser validates both sides."""
import tomllib
from .transform import reference,RESOURCE_FIELDS

def rewrite(text):
    tomllib.loads(text);i=0;edits=[];changes=[]
    while i<len(text):
        if text[i]=='#':
            end=text.find('\n',i);i=len(text) if end<0 else end+1;continue
        if text[i] not in "\"'":i+=1;continue
        start=i;q=text[i];triple=text[i:i+3]==q*3;delimiter=q*3 if triple else q;i+=len(delimiter)
        while i<len(text):
            if text[i:i+len(delimiter)]==delimiter:
                i+=len(delimiter);break
            if q=='"' and text[i]=='\\':i+=2
            else:i+=1
        else:raise ValueError('Unclosed TOML string')
        token=text[start:i];value=tomllib.loads('value='+token)['value'];line=text[text.rfind('\n',0,start)+1:start]
        key=line.split('=',1)[0].strip() if '=' in line else ''
        new=value if key in RESOURCE_FIELDS else reference(value)
        if new!=value:
            # Role IDs need no escaping; use the existing quote style.
            edits.append((start,i,delimiter+new+delimiter));changes.append(key or 'dimension-string')
    for start,end,value in reversed(edits):text=text[:start]+value+text[end:]
    tomllib.loads(text)
    return text,changes

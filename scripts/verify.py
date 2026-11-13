#!/usr/bin/env python3
"""Inspect real class files, CLDC StackMap attributes and delivery metadata."""
import struct, zipfile, hashlib
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
import os
DIST = Path(os.environ.get('KISCPORT_DIST', ROOT/'dist')).resolve()
class Reader:
    def __init__(self,data): self.data=data; self.pos=0
    def take(self,n):
        x=self.data[self.pos:self.pos+n]; self.pos+=n
        assert len(x)==n, 'truncated class'
        return x
    def u1(self): return self.take(1)[0]
    def u2(self): return struct.unpack('>H',self.take(2))[0]
    def u4(self): return struct.unpack('>I',self.take(4))[0]

def inspect(data):
    r=Reader(data); assert r.u4()==0xcafebabe
    minor,major=r.u2(),r.u2(); assert (major,minor)==(45,3), (major,minor)
    count=r.u2(); cp=[None]*count; tags=[None]*count; i=1
    while i<count:
        tag=r.u1()
        tags[i]=tag
        assert tag in (1,3,4,5,6,7,8,9,10,11,12), 'modern constant pool tag'
        if tag==1: cp[i]=r.take(r.u2()).decode('utf-8',errors='replace')
        elif tag in (5,6): r.take(8); i+=1
        elif tag in (3,4,9,10,11,12): r.take(4)
        else: r.take(2)
        i+=1
    r.take(6); r.take(2*r.u2())
    maps=0
    def attrs(reader, code=False):
        nonlocal maps
        for _ in range(reader.u2()):
            name=cp[reader.u2()]; payload=reader.take(reader.u4())
            assert name!='StackMapTable', 'Java SE stack map in CLDC artifact'
            if name=='Code':
                c=Reader(payload); c.take(4); bytecode=c.take(c.u4()); c.take(8*c.u2()); attrs(c,bytecode)
                assert c.pos==len(c.data)
            elif name=='StackMap':
                assert code is not False
                sm=Reader(payload); previous=-1
                for _ in range(sm.u2()):
                    offset=sm.u2(); assert previous<offset<len(code); previous=offset
                    for _ in range(2):
                        for _ in range(sm.u2()):
                            kind=sm.u1(); assert kind<=8
                            if kind==7:
                                index=sm.u2(); assert 0<index<len(cp) and tags[index]==7
                            elif kind==8:
                                index=sm.u2(); assert index<len(code) and code[index]==0xbb
                assert sm.pos==len(sm.data); maps+=1
    for _ in range(2):
        for _ in range(r.u2()): r.take(6); attrs(r)
    attrs(r); assert r.pos==len(data)
    return maps

def props(data):
    assert b'\r\n' in data and b'\n' not in data.replace(b'\r\n',b''), 'descriptor requires CRLF'
    lines=data.decode('ascii').replace('\r\n','\n').splitlines()
    assert all(len(line.encode('ascii'))<=72 for line in lines)
    entries=[line.split(': ',1) for line in lines if line]
    assert all(len(entry)==2 and entry[0] and entry[1] for entry in entries)
    assert len(entries)==len(set(entry[0] for entry in entries)), 'duplicate descriptor attribute'
    return dict(entries)

def main():
    jar=DIST/'KiscPortDemo.jar'; jad=DIST/'KiscPortDemo.jad'
    j=props(jad.read_bytes()); assert j['MIDlet-Jar-Size']==str(jar.stat().st_size)
    assert j['MIDlet-Jar-URL']==jar.name
    with zipfile.ZipFile(jar) as z:
        assert z.testzip() is None
        m=props(z.read('META-INF/MANIFEST.MF'))
        for k,v in m.items():
            if k!='Manifest-Version': assert j[k]==v, k
        assert m['MicroEdition-Configuration']=='CLDC-1.1'
        assert m['MicroEdition-Profile']=='MIDP-2.0'
        assert m['Manifest-Version']=='1.0'
        for key in ('MIDlet-Name','MIDlet-Version','MIDlet-Vendor','MIDlet-1'):
            assert m[key] and m[key]==j[key], key
        assert not any(k.startswith(('MIDlet-Permissions','MIDlet-Certificate','MIDlet-Jar-RSA')) for k in j), 'unexpected permissions/signature'
        assert m['MIDlet-1'].endswith(',kiscport.platform.KiscPortMIDlet')
        names=z.namelist(); assert len(names)==len(set(names))
        assert 'kiscport/platform/KiscPortMIDlet.class' in names
        total=0; classes=0
        for name in names:
            assert name=='META-INF/MANIFEST.MF' or name.startswith('kiscport/'), name
            if name.endswith('.class'):
                total+=inspect(z.read(name)); classes+=1
        assert total>0, 'CLDC preverification attributes missing'
    print('PASS: metadata, ZIP integrity, application-only contents, bytecode 45.3, CLDC StackMap')
    print('classes=%d StackMap methods=%d bytes=%d SHA256=%s' % (classes,total,jar.stat().st_size,hashlib.sha256(jar.read_bytes()).hexdigest()))
if __name__=='__main__': main()

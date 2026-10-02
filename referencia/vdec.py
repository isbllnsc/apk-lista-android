# python vdec.py file.zip [window-substring] -> decodes ViewHierarchyEncoder dumps
import sys, zipfile, struct
def parse(buf):
    p=0
    def tok():
        nonlocal p
        s=buf[p:p+1]; p+=1
        if s==b'M': return ('M',None)
        if s==b'S': v=struct.unpack('>h',buf[p:p+2])[0]; p+=2; return ('S',v)
        if s==b'I': v=struct.unpack('>i',buf[p:p+4])[0]; p+=4; return ('I',v)
        if s==b'Z': v=buf[p]!=0; p+=1; return ('Z',v)
        if s==b'B': v=buf[p]; p+=1; return ('B',v)
        if s==b'F': v=struct.unpack('>f',buf[p:p+4])[0]; p+=4; return ('F',v)
        if s==b'D': v=struct.unpack('>d',buf[p:p+8])[0]; p+=8; return ('D',v)
        if s==b'J': v=struct.unpack('>q',buf[p:p+8])[0]; p+=8; return ('J',v)
        if s==b'R': n=struct.unpack('>h',buf[p:p+2])[0]; p+=2; v=buf[p:p+n].decode('utf-8','replace'); p+=n; return ('R',v)
        raise ValueError('sig %r at %d'%(s,p-1))
    def obj():
        d=[]
        while True:
            t,k=tok()
            assert t=='S'
            if k==0: return d
            t,v=tok()
            if t=='M': v=obj()
            d.append((k,v))
    objs=[]
    while p<len(buf):
        t,v=tok()
        if t=='M': objs.append(obj())
        else: tok()
    names={}
    table=objs[-1]
    # table: pairs (short idx -> string) written as writeShort(idx) writeString(name) => parsed as key=idx,value=name
    for k,v in table:
        if isinstance(v,str): names[k]=v
    return objs[0],names
def walk(o,names,depth,out,ox=0,oy=0):
    d={names.get(k,k):v for k,v in o if not isinstance(v,list)}
    cls=str(d.get('meta:__name__','?')).split('.')[-1]
    L=ox+(d.get('layout:left') or 0)+int(d.get('drawing:translationX') or 0);T=oy+(d.get('layout:top') or 0)+int(d.get('drawing:translationY') or 0);R=L+(d.get('layout:width') or 0);B=T+(d.get('layout:height') or 0)
    vis=d.get('misc:visibility');idv=d.get('id')
    if vis not in (None,0,'VISIBLE'): return
    if cls.endswith('LayoutParams') or cls in ('Resources$Theme','00UL') or cls.startswith('Resources'): return
    tag=[cls]
    if idv not in (None,'NO_ID',-1): tag.append(str(idv).replace('com.instagram.android:id/','#'))
    if d.get('misc:clickable'): tag.append('C')
    if d.get('checked'): tag.append('K')
    if d.get('misc:selected'): tag.append('S')
    if d.get('misc:enabled') is False: tag.append('X')
    if d.get('focus:isFocused') or d.get('focus:focused'): tag.append('F')
    tag.append('[%d,%d][%d,%d]'%(L,T,R,B))
    out.append('  '*depth+' '.join(tag))
    sx=d.get('scrolling:mScrollX') or d.get('scrolling:scrollX') or 0; sy=d.get('scrolling:mScrollY') or d.get('scrolling:scrollY') or 0
    for k,v in o:
        if isinstance(v,list): walk(v,names,depth+1,out,L-sx,T-sy)
z=zipfile.ZipFile(sys.argv[1])
for i in z.infolist():
    if len(sys.argv)>2 and sys.argv[2] not in i.filename: continue
    try: root,names=parse(z.read(i))
    except Exception as e: print('=== '+i.filename+' (falhou: %s, %d bytes)'%(e,i.file_size)); continue
    out=[]; walk(root,names,0,out)
    print('=== '+i.filename); print('\n'.join(out))
    if len(sys.argv)>3: print(sorted(set(names.values())))

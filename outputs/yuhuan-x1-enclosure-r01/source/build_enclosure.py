"""语环 X1 可打印样壳。运行环境：CadQuery 2.5.2 / Python 3.9+。单位 mm。"""
from pathlib import Path
import argparse
import hashlib
import json
import math
import struct
import zipfile
from collections import Counter
import cadquery as cq

HERE = Path(__file__).resolve().parent

def rounded_box(x0, y0, x1, y1, radius, z0, height):
    return (cq.Workplane('XY').box(x1-x0, y1-y0, height, centered=(True, True, False))
            .edges('|Z').fillet(radius).translate(((x0+x1)/2, (y0+y1)/2, z0)))

def box_at(x0, y0, x1, y1, z0, height):
    return cq.Workplane('XY').box(x1-x0, y1-y0, height, centered=(False, False, False)).translate((x0,y0,z0))

def cylinder(x, y, r, z, h):
    return cq.Workplane('XY').center(x,y).circle(r).extrude(h).translate((0,0,z))

def shape(w):
    return w.val() if isinstance(w,cq.Workplane) else w

def volume(w):
    return sum(s.Volume() for s in shape(w).Solids())

def overlap(a,b):
    return sum(s.Volume() for s in shape(a).intersect(shape(b)).Solids())

def xy(c):
    return ((c['y']-98)*0.0254, (2447-c['x'])*0.0254)

def component_bbox(c):
    b = c['bbox']
    return ((b['minY']-98)*0.0254, (2447-b['maxX'])*0.0254,
            (b['maxY']-98)*0.0254, (2447-b['minX'])*0.0254)

def mesh_info(s, destination):
    cq.exporters.export(s, str(destination), exportType='STL', tolerance=0.015, angularTolerance=0.08)
    data=destination.read_bytes()
    count=struct.unpack_from('<I',data,80)[0]
    assert len(data)==84+50*count, destination
    edges=Counter()
    mesh_volume=0.0
    minimum=[float('inf')]*3;maximum=[-float('inf')]*3
    for i in range(count):
        values=struct.unpack_from('<12fH',data,84+i*50)
        v=[tuple(values[3+j*3:6+j*3]) for j in range(3)]
        assert len(set(v))==3, ('degenerate triangle',destination,i)
        for p in v:
            for j in range(3):minimum[j]=min(minimum[j],p[j]);maximum[j]=max(maximum[j],p[j])
        for j in range(3):edges[tuple(sorted((v[j],v[(j+1)%3])))]+=1
        a,b,c=v
        mesh_volume+=(a[0]*(b[1]*c[2]-b[2]*c[1])-a[1]*(b[0]*c[2]-b[2]*c[0])+a[2]*(b[0]*c[1]-b[1]*c[0]))/6
    bad=sum(n!=2 for n in edges.values())
    assert bad==0, ('non-manifold edges',destination,bad)
    assert mesh_volume>0
    assert all(v>=-1e-5 for v in minimum)
    return {'triangles':count,'watertight_edges':True,'nonmanifold_edges':bad,
            'mesh_volume_mm3':round(mesh_volume,4),
            'bbox_mm':{'min':minimum,'max':maximum},'bytes':len(data),
            'sha256':hashlib.sha256(data).hexdigest()}

def move_to_origin(s):
    b=s.BoundingBox()
    return s.translate((-b.xmin,-b.ymin,-b.zmin))

def build(p, out):
    out.mkdir(parents=True,exist_ok=True)
    for name in ('stl','step','reference','verification','source','preview'):(out/name).mkdir(exist_ok=True)
    pcb=json.loads((HERE/'pcb-live.json').read_text())
    parts={c['designator']:c for c in pcb['components']}
    L,W=p['pcb']['length'],p['pcb']['width']
    H=p['case']['height'];floor=p['case']['floor'];skin=p['case']['lid']
    margin=p['case']['margin'];wall=p['case']['wall'];R=p['case']['corner_radius']
    top_z=floor+p['pcb']['bottom_gap']
    component_z=top_z+p['pcb']['thickness']
    seam=H-skin
    base_boss_top=H-2.8
    print('PCB underside/top',top_z,component_z,'lid underside',seam,flush=True)
    x0,y0,x1,y1=-margin,-margin,L+margin,W+margin
    screw_xy=[(-1.5,-1.5),(L+1.5,-1.5),(L+1.5,W+1.5),(-1.5,W+1.5)]
    outer=rounded_box(x0,y0,x1,y1,R,0,seam)
    cavity=rounded_box(x0+wall,y0+wall,x1-wall,y1-wall,R-wall,floor,seam+1)
    base=outer.cut(cavity)
    # 四个螺母柱在 PCB 外。M2 螺母从底部装入，顶部肩台承受拧紧载荷。
    for x,y in screw_xy:
        base=base.cut(cylinder(x,y,p['screw']['boss_diameter']/2+.15,base_boss_top,H))
        base=base.union(cylinder(x,y,p['screw']['boss_diameter']/2,0,base_boss_top))
    # 只在 USB 壳体插脚和电池焊点下局部下沉 0.35 mm，不整体抬高 PCB。
    for name in ['J1','BAT1']:
        for pad in parts[name]['pads']:
            if pad['layer']!=12:continue
            x=(pad['y']-98)*.0254;y=(2447-pad['x'])*.0254
            base=base.cut(rounded_box(x-pad['height']*.0254/2-.30,y-pad['width']*.0254/2-.30,
                                      x+pad['height']*.0254/2+.30,y+pad['width']*.0254/2+.30,
                                      .3,floor-.35,1.0))
    # PCB 无螺丝孔；底面托点和上盖限位压脚固定 PCB 边缘。
    supports=[(20,1.4),(45,1.4),(65,1.4),(20,W-1.4),(45,W-1.4),(61,W-1.4),
              (1.4,30),(1.4,43),(L-1.4,40),(L-1.4,68)]
    for x,y in supports:
        if x<3 or x>L-3:
            base=base.union(box_at(x-2.1,y-2.1,x+2.1,y+2.1,floor,top_z-floor))
        else:
            base=base.union(box_at(x-2.4,y-2.0,x+2.4,y+2.0,floor,top_z-floor))
        # 单边 0.25 mm 的侧向定位，不以按键柱或 USB 焊脚承受 PCB 移动。
        locator_h=component_z-floor+.4
        if x<3:base=base.union(box_at(-1.25,y-3,-.25,y+3,floor,locator_h))
        elif x>L-3:base=base.union(box_at(L+.25,y-3,L+1.25,y+3,floor,locator_h))
        elif y<W/2:base=base.union(box_at(x-3,-1.25,x+3,-.25,floor,locator_h))
        else:base=base.union(box_at(x-3,W+.25,x+3,W+1.25,floor,locator_h))
    lid=rounded_box(x0,y0,x1,y1,R,seam,skin)
    # 上盖内侧定位圈，单边 0.20 mm；螺柱和接口处让位。
    lip_outer=rounded_box(x0+wall+.20,y0+wall+.20,x1-wall-.20,y1-wall-.20,R-wall-.20,seam-.9,1.0)
    lip_inner=rounded_box(x0+wall+1.0,y0+wall+1.0,x1-wall-1.0,y1-wall-1.0,max(.4,R-wall-1.0),seam-1.0,1.3)
    lip=lip_outer.cut(lip_inner)
    for x,y in screw_xy:lip=lip.cut(cylinder(x,y,3.5,seam-1.2,1.5))
    lid=lid.union(lip)
    # 在电池包络之外布置 0.6 mm 内筋，增强 1 mm 上盖；不向电池施压。
    bs=p['pcb']['battery_slot']
    rib_outer=rounded_box(bs['x']-2.5,bs['y']-2.5,bs['x']+bs['length']+2.5,bs['y']+bs['width']+2.5,1.3,seam-.6,.7)
    rib_inner=rounded_box(bs['x']-1.5,bs['y']-1.5,bs['x']+bs['length']+1.5,bs['y']+bs['width']+1.5,.4,seam-.7,.9)
    lid=lid.union(rib_outer.cut(rib_inner))
    for x,y in screw_xy:lid=lid.union(cylinder(x,y,p['screw']['boss_diameter']/2,base_boss_top+.05,seam-base_boss_top+.05))
    for x,y in supports:
        # 0.10 mm 竖向余量；没有元件的板边作为机械限位面。
        lid=lid.union(box_at(x-1.2,y-1.2,x+1.2,y+1.2,component_z+.10,seam-component_z))
    for x,y in screw_xy:
        nut=cq.Workplane('XY').center(x,y).polygon(6,p['screw']['nut_pocket_af']/math.cos(math.pi/6)).extrude(p['screw']['nut_seat_z'])
        base=base.cut(nut).cut(cylinder(x,y,p['screw']['clearance']/2,0,H+1))
        lid=lid.cut(cylinder(x,y,p['screw']['clearance']/2,0,H+1))
        lid=lid.cut(cylinder(x,y,p['screw']['head_recess_diameter']/2,p['screw']['head_seat_z'],H+1))
    # USB-C：开口同时容纳一部分插头包胶，以补偿插座对板框的内缩。
    ux,uy=xy(parts['J1']);usb_center_z=component_z+p['usb']['center_above_pcb']
    recess=rounded_box(p['usb']['recess_end_x'],uy-9.3,x1+2,uy+9.3,1.0,-.1,H+.3)
    base=base.cut(recess);lid=lid.cut(recess)
    usb=(cq.Workplane('YZ').center(uy,usb_center_z).rect(p['usb']['opening_width'],p['usb']['opening_height'])
         .extrude(22).edges('|X').fillet(p['usb']['opening_radius']).translate((90,0,0)))
    base=base.cut(usb);lid=lid.cut(usb)
    # MicroSD：沿板边的开放式指甲槽，不用从深孔里抠卡。
    sx,sy=xy(parts['J2'])
    sd=rounded_box(sx-p['sd']['opening_width']/2,y0-1,sx+p['sd']['opening_width']/2,5.0,1.2,
                   component_z+.05,H+2)
    base=base.cut(sd);lid=lid.cut(sd)
    # 底部进声的四麦：四条等长声路，中心来自封装 FILL e26，不取器件锚点。
    microphones=[]
    for name in ('MIC1','MIC2','MIC3','MIC4'):
        c=parts[name];assert c['rotation']==0
        x=(c['y']-31.89-98)*.0254;y=(2447-(c['x']-.005))*.0254
        base=base.cut(cylinder(x,y,p['acoustic']['case_hole_diameter']/2,-.1,H+1))
        microphones.append({'name':name,'x':x,'y':y,'pcb_hole_diameter':.7})
    # 三个真实灯位；首版直接开观察孔，可后续更换透明导光件。
    for name,d in [('LED1',1.7),('LED2',1.7),('D1',3.2)]:
        x,y=xy(parts[name]);lid=lid.cut(cylinder(x,y,d/2,seam-.1,skin+.2))
    # 两个无卡扣的独立按键帽：法兰防脱、开关提供回弹。
    buttons={}
    button_details=[]
    for name,label in [('SW1','record'),('SW2','recall')]:
        x,y=xy(parts[name])
        flange_top=seam-.18
        flange_bottom=flange_top-p['button']['flange_thickness']
        actuator_z=component_z+p['button']['switch_height']
        stem_bottom=actuator_z+p['button']['free_gap']
        lid=lid.cut(cylinder(x,y,p['button']['hole_diameter']/2,seam-.2,skin+.5))
        guide=cylinder(x,y,4.1,flange_bottom-.5,seam-flange_bottom+.6).cut(cylinder(x,y,3.55,flange_bottom-.6,seam-flange_bottom+.8))
        lid=lid.union(guide)
        cap=cylinder(x,y,p['button']['flange_diameter']/2,flange_bottom,p['button']['flange_thickness'])
        cap=cap.union(cylinder(x,y,p['button']['cap_diameter']/2,flange_top-.1,H+p['button']['protrusion']-flange_top+.1))
        cap=cap.union(cylinder(x,y,p['button']['stem_diameter']/2,stem_bottom,flange_bottom-stem_bottom+.1))
        # 录音键为圆点，回溯键为双短线，凹刻而非凸起细节。
        if label=='record':cap=cap.cut(cylinder(x,y,.6,H+p['button']['protrusion']-.20,.4))
        else:
            for dx in [-.65,.65]:cap=cap.cut(box_at(x+dx-.18,y-.8,x+dx+.18,y+.8,H+p['button']['protrusion']-.2,.4))
        buttons[label]=cap
        button_details.append({'switch':name,'name':label,'x':x,'y':y,'stem_bottom':stem_bottom,
                               'actuator_top':actuator_z,'free_gap':p['button']['free_gap']})
    # 字体形成凹字；不加入单独悬浮字片。
    word=(cq.Workplane('XY').text('YUHUAN X1',4.8,.25,font='Arial',halign='left',valign='center',combine=False)
          .translate((13,43,H-.20)))
    for letter in word.solids().vals():lid=lid.cut(letter)
    sub=(cq.Workplane('XY').text('CAPTURE THE MOMENT',1.65,.22,font='Arial',halign='left',valign='center',combine=False)
         .translate((13,37.8,H-.17)))
    for letter in sub.solids().vals():lid=lid.cut(letter)
    # 电池贯通槽；这是 PCB 本体的开槽，不是仅禁止铺铜区域。
    slot=p['pcb']['battery_slot']
    pcb_body=rounded_box(0,0,L,W,p['pcb']['corner_radius'],top_z,p['pcb']['thickness'])
    pcb_body=pcb_body.cut(box_at(slot['x'],slot['y'],slot['x']+slot['length'],slot['y']+slot['width'],top_z-.1,p['pcb']['thickness']+.2))
    for m in microphones:pcb_body=pcb_body.cut(cylinder(m['x'],m['y'],.35,top_z-.1,p['pcb']['thickness']+.2))
    battery=rounded_box(slot['x']+(slot['length']-p['battery']['nominal_x'])/2,
                        slot['y']+(slot['width']-p['battery']['nominal_y'])/2,
                        slot['x']+(slot['length']+p['battery']['nominal_x'])/2,
                        slot['y']+(slot['width']+p['battery']['nominal_y'])/2,.8,
                        floor+p['battery']['tape'],p['battery']['nominal_z'])
    battery_envelope=box_at(slot['x']+p['battery']['slot_edge_clearance'],slot['y']+p['battery']['slot_edge_clearance'],
                             slot['x']+slot['length']-p['battery']['slot_edge_clearance'],
                             slot['y']+slot['width']-p['battery']['slot_edge_clearance'],
                             floor+p['battery']['tape'],p['battery']['assumed_max_z'])
    shells={'base':base,'lid':lid,**{f'button_{k}':v for k,v in buttons.items()}}
    checks={}
    for name,part in shells.items():
        part=shape(part)
        assert part.isValid(), ('invalid BRep',name)
        assert len(part.Solids())==1, ('not single solid',name,len(part.Solids()))
        checks[name]={'brep_valid':True,'solids':1,'volume_mm3':volume(part),
                      'pcb_interference_mm3':overlap(part,pcb_body),
                      'battery_envelope_interference_mm3':overlap(part,battery_envelope)}
        assert checks[name]['pcb_interference_mm3']<1e-6, checks[name]
        assert checks[name]['battery_envelope_interference_mm3']<1e-6, checks[name]
    for i,(a,sa) in enumerate(shells.items()):
        for b,sb in list(shells.items())[i+1:]:
            v=overlap(sa,sb);assert v<1e-6, ('printed part collision',a,b,v)
    # 库 bbox 是保守的二维包络；高度按参数中的预留值建模，不冒充详细 STEP。
    envelopes=[]
    hardware_interferences=[]
    for name,c in parts.items():
        if name=='BAT1':continue # 首版明确不装直立 XH 插座，直接焊电池引线。
        bx0,by0,bx1,by1=component_bbox(c)
        height=p['component_height'].get(name, p['component_height']['default'])
        env=box_at(bx0,by0,bx1,by1,component_z,height)
        envelopes.append((name,env))
        for sname,s in [('base',base),('lid',lid)]:
            v=overlap(env,s)
            if v>1e-6:hardware_interferences.append({'component':name,'shell':sname,'mm3':v})
    assert not hardware_interferences, hardware_interferences
    checks['component_envelopes']={'tested':len(envelopes),'shell_collisions':hardware_interferences,
                                   'status':'assumed height envelopes; not actual component STEP geometry'}
    # 按键压到底的最大设计行程，不应与导向套或壳体相交。
    for key,part in buttons.items():
        moved=part.translate((0,0,-p['button']['maximum_downstroke']))
        assert overlap(moved,lid)<1e-6, ('button stroke lid collision',key)
    exports={}
    for name,w in shells.items():
        s=shape(w)
        if name=='lid':s=s.rotate((0,0,0),(1,0,0),180)
        s=move_to_origin(s)
        exports[name]=mesh_info(s,out/'stl'/f'x1_{name}.stl')
        cq.exporters.export(s,str(out/'step'/f'x1_{name}.step'))
    # 简短接口与螺母/按键间隙试片，应优先打印，以选定实际树脂收缩补偿。
    coupon=rounded_box(0,0,64,28,2,0,3)
    for x,af in [(7,4.10),(18,4.25),(29,4.40)]:
        cut=cq.Workplane('XY').center(x,8).polygon(6,af/math.cos(math.pi/6)).extrude(3.3)
        coupon=coupon.cut(cut)
    for x,d in [(40,4.9),(49,5.1),(58,5.3)]:coupon=coupon.cut(cylinder(x,8,d/2,-.1,3.3))
    port=rounded_box(5,16,5+p['usb']['opening_width'],16+p['usb']['opening_height'],p['usb']['opening_radius'],-.1,3.3)
    coupon=coupon.cut(port)
    for x,label in [(4,'4.10'),(15,'4.25'),(26,'4.40'),(37,'4.9'),(46,'5.1'),(55,'5.3')]:
        text=(cq.Workplane('XY').text(label,1.5,.3,font='Arial',halign='left',combine=False).translate((x,12,2.8)))
        for letter in text.solids().vals():coupon=coupon.cut(letter)
    assert shape(coupon).isValid() and len(shape(coupon).Solids())==1
    exports['fit_coupon']=mesh_info(shape(coupon),out/'stl'/'x1_fit_coupon.stl')
    # 槽口 1:1 薄片，验证带保护板和封边的电池成品是否能穿过，不含电池。
    gauge=rounded_box(0,0,slot['length']+8,slot['width']+8,2,0,1.6)
    gauge=gauge.cut(box_at(4,4,4+slot['length'],4+slot['width'],-.1,2))
    exports['battery_slot_gauge']=mesh_info(shape(gauge),out/'stl'/'x1_battery_slot_gauge.stl')
    # 彩色 STEP 装配：真实 PCB 外形/开槽；电池与器件仅参考包络。
    assy=cq.Assembly(name='X1_R01_PROTOTYPE_NOT_PHYSICALLY_FIT_TESTED')
    assy.add(shape(base),name='base',color=cq.Color(.80,.84,.82))
    assy.add(shape(lid),name='lid',color=cq.Color(.93,.94,.89))
    for key,v in buttons.items():assy.add(shape(v),name=f'button_{key}',color=cq.Color(0,.28,.25))
    assy.add(shape(pcb_body),name='PCB_WITH_BATTERY_CUTOUT_reference',color=cq.Color(.05,.32,.24))
    assy.add(shape(battery),name='BATTERY_NOMINAL_ONLY_NOT_MEASURED',color=cq.Color(.67,.69,.70))
    for name,v in envelopes:assy.add(shape(v),name=f'{name}_HEIGHT_ENVELOPE',color=cq.Color(.38,.39,.41))
    hardware=[]
    for i,(x,y) in enumerate(screw_xy):
        bolt=cylinder(x,y,1.0,p['screw']['head_seat_z']-5,5).union(cylinder(x,y,1.9,p['screw']['head_seat_z'],1.4))
        nut=(cq.Workplane('XY').center(x,y).polygon(6,4.0/math.cos(math.pi/6)).extrude(1.6)
             .translate((0,0,p['screw']['nut_seat_z']-1.6)))
        nut=nut.cut(cylinder(x,y,1.05,0,H))
        for label,v in [('M2x5_unthreaded_reference',bolt),('M2_nut_unthreaded_reference',nut)]:
            assert overlap(v,base)<1e-6 and overlap(v,lid)<1e-6, ('hardware collision',i,label)
            assy.add(shape(v),name=f'{label}_{i}',color=cq.Color(.2,.21,.22))
            hardware.append((f'{label}_{i}',v,[.13,.14,.15,1]))
    assy.save(str(out/'step'/'x1_assembly_reference.step'))
    cq.exporters.export(pcb_body,str(out/'reference'/'pcb_with_cutout.step'))
    # 渲染中间数据；可在 Blender 无插件环境下重建。
    render=[]
    items=[('base',base,[.67,.75,.72,1]),('lid',lid,[.90,.92,.85,1]),
           ('button_record',buttons['record'],[.008,.20,.18,1]),('button_recall',buttons['recall'],[.008,.20,.18,1]),
           ('PCB',pcb_body,[.015,.23,.16,1]),('battery',battery,[.58,.61,.64,1])]
    items += [(name,v,[.24,.25,.28,1]) for name,v in envelopes if name in ['U1','J1','J2','SW1','SW2','D1','MIC1','MIC2','MIC3','MIC4']]
    items += hardware
    for name,v,color in items:
        vertices,triangles=shape(v).tessellate(.04,.12)
        render.append({'name':name,'vertices':[[q.x,q.y,q.z] for q in vertices],'faces':triangles,'color':color})
    (out/'preview'/'render-meshes.json').write_text(json.dumps(render,separators=(',',':')))
    report={'revision':'R01','units':'mm','status':'printable prototype geometry; physical fit unverified',
            'case_body_mm':[L+2*margin,W+2*margin,H],
            'maximum_height_with_buttons_mm':H+p['button']['protrusion'],
            'pcb_mm':[L,W,p['pcb']['thickness']],'pcb_z':[top_z,component_z],
            'slot':slot,'battery_measured':False,
            'battery_nominal_xyz':[p['battery']['nominal_x'],p['battery']['nominal_y'],p['battery']['nominal_z']],
            'battery_assumed_fit_envelope_xyz':[slot['length']-2*p['battery']['slot_edge_clearance'],slot['width']-2*p['battery']['slot_edge_clearance'],p['battery']['assumed_max_z']],
            'slot_nominal_clearance_per_side_xy':[(slot['length']-p['battery']['nominal_x'])/2,(slot['width']-p['battery']['nominal_y'])/2],
            'ceiling_z':seam,'battery_max_to_ceiling_mm':seam-floor-p['battery']['tape']-p['battery']['assumed_max_z'],
            'screws':screw_xy,'microphone_ports':microphones,'buttons':button_details,
            'printed_parts':checks,'mesh_exports':exports,'physical_print_test':False,
            'electronic_board_modified':False,'omit_component':'BAT1 vertical XH-2A, user authorized direct battery leads',
            'note':'PCB xy and slot verified by native readback; PCB thickness and component height envelopes are design assumptions.'}
    (out/'verification'/'geometry-check.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    (out/'source'/'parameters.json').write_text(json.dumps(p,ensure_ascii=False,indent=2))
    (out/'source'/'pcb-live.json').write_text(json.dumps(pcb,ensure_ascii=False,indent=2))
    print(json.dumps({k:v for k,v in report.items() if k not in ['printed_parts','mesh_exports']},ensure_ascii=False,indent=2))

if __name__=='__main__':
    ap=argparse.ArgumentParser()
    ap.add_argument('--parameters',default=str(HERE/'parameters.json'))
    ap.add_argument('--out',required=True)
    args=ap.parse_args()
    build(json.loads(Path(args.parameters).read_text()),Path(args.out))

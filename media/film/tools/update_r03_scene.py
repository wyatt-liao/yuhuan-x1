"""Replace the film's old concept shell with the R03 CAD tessellation.

Run: blender -b assets/Voloop-X1-CG.blend --python tools/update_r03_scene.py --
The camera, lighting and motion come from the existing approved continuous shot.
"""
import argparse
import bpy
import json
import math
import sys
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument('--root',help='Optional repository root; defaults to the folder containing this film project')
parser.add_argument('--preview',action='store_true')
parser.add_argument('--preview-dir',help='Optional directory for preview stills')
args=parser.parse_args(sys.argv[sys.argv.index('--')+1:])
film=Path(args.root)/'media/film' if args.root else Path(__file__).resolve().parents[1]
preview=Path(args.preview_dir) if args.preview_dir else film/'preview'
scene=bpy.context.scene
root=bpy.data.objects.get('Voloop PCB aligned assembly') or bpy.data.objects.get('Yuhuan X1 R03 assembly')
assert root,'Missing product animation root'
for obj in list(root.children_recursive):
    bpy.data.objects.remove(obj,do_unlink=True)
root.name='Yuhuan X1 R03 assembly'

def linear(value):
    value=value/255
    return value/12.92 if value<.04045 else ((value+.055)/1.055)**2.4

def material(name,color,metal,roughness):
    mat=bpy.data.materials.new(name)
    mat.use_nodes=True
    bs=mat.node_tree.nodes.get('Principled BSDF')
    rgb=[linear(int(color[i:i+2],16)) for i in (1,3,5)]
    bs.inputs['Base Color'].default_value=(*rgb,1)
    bs.inputs['Metallic'].default_value=metal
    bs.inputs['Roughness'].default_value=roughness
    return mat

mats={
 'metal':material('R03 satin aluminum intent','#bcc3c9',.96,.30),
 'plastic':material('R03 matching silver RF polymer','#b5bdc2',0,.39),
 'button':material('R03 graphite polymer buttons','#596169',0,.42),
 'pcb':material('R03 PCB soldermask','#14564a',.15,.52),
 'battery':material('R03 battery pouch','#a8b2b9',.55,.42),
 'fastener':material('R03 steel hardware','#7b858d',.85,.31),
 'nylon':material('R03 RF corner nylon hardware','#7b858d',0,.45),
 'component':material('R03 component envelope reference','#474f56',.2,.44),
}
assembly=json.loads((film/'assets/r03-assembly.json').read_text())
objects={}
for item in assembly:
    name=item['name']
    if name=='BUTTONS_SPRUE_OPTIONAL':
        continue
    origin=(0,0,0)
    if name.startswith('button'):
        bounds=[(min(v[i] for v in item['vertices'])+max(v[i] for v in item['vertices']))/2 for i in range(3)]
        origin=((bounds[0]-49.9999)/10,(bounds[1]-37.0205)/10,(bounds[2]-4.9)/10)
    verts=[((v[0]-49.9999)/10-origin[0],(v[1]-37.0205)/10-origin[1],(v[2]-4.9)/10-origin[2]) for v in item['vertices']]
    mesh=bpy.data.meshes.new('R03 '+name)
    mesh.from_pydata(verts,[],item['faces'])
    mesh.update()
    obj=bpy.data.objects.new('SW1 actuator' if name=='button_record' else 'SW2 actuator' if name=='button_recall' else 'R03 '+name,mesh)
    scene.collection.objects.link(obj)
    obj.parent=root
    obj.location=origin
    key='metal' if name in ('base_metal','lid_metal') else 'plastic' if 'rf_plastic' in name else 'button' if name.startswith('button') else 'pcb' if name=='PCB' else 'battery' if name=='battery' else 'nylon' if 'NYLON' in name else 'fastener' if 'reference' in name else 'component'
    mesh.materials.append(mats[key])
    # The imported CAD tessellation preserves actual openings, joints and fasteners.
    objects[name]=obj

def ease(value):
    value=max(0,min(1,value))
    return value*value*value*(value*(value*6-15)+10)

cap=objects['button_recall']
base=cap.location.z
for frame in range(1,1051):
    t=(frame-1)/30
    pulse=max(0,*[1-ease(abs(t-hit)/.10) for hit in [10.95,13.10,13.32,13.54]])
    cap.location.z=base-.020*pulse
    cap.keyframe_insert(data_path='location',frame=frame)
for fc in cap.animation_data.action.fcurves:
    for point in fc.keyframe_points:
        point.interpolation='LINEAR'

# The phone now shows the actual Android Demo, rather than invented AI cards.
screen=bpy.data.materials.get('Phone display')
assert screen,'Missing phone screen material'
nodes=screen.node_tree.nodes
nodes.clear()
output=nodes.new('ShaderNodeOutputMaterial')
emission=nodes.new('ShaderNodeEmission')
emission.inputs['Strength'].default_value=.8
today=nodes.new('ShaderNodeTexImage')
today.image=bpy.data.images.load(str(film/'assets/android-today.png'),check_existing=True)
detail=nodes.new('ShaderNodeTexImage')
detail.image=bpy.data.images.load(str(film/'assets/android-detail.png'),check_existing=True)
mix=nodes.new('ShaderNodeMixRGB')
screen.node_tree.links.new(today.outputs['Color'],mix.inputs[1])
screen.node_tree.links.new(detail.outputs['Color'],mix.inputs[2])
mix.inputs[0].default_value=0
mix.inputs[0].keyframe_insert('default_value',frame=1)
mix.inputs[0].keyframe_insert('default_value',frame=23*30+1)
mix.inputs[0].default_value=1
mix.inputs[0].keyframe_insert('default_value',frame=23.5*30+1)
screen.node_tree.links.new(mix.outputs[0],emission.inputs['Color'])
screen.node_tree.links.new(emission.outputs[0],output.inputs['Surface'])
scene.render.resolution_percentage=100
scene.render.engine='BLENDER_EEVEE_NEXT'
scene.eevee.taa_render_samples=32
scene.render.image_settings.file_format='PNG'
scene.render.image_settings.color_mode='RGBA'
scene.render.film_transparent=True
# High contrast was crushing the neutral RF polymer; keep both silver parts legible.
scene.view_settings.look='AgX - Medium High Contrast'
scene['product_name']='语环 X1'
scene['enclosure_revision']='R03'
scene['body_dimensions_mm']=[110.7998,84.841,9.8]
scene['button_maximum_mm']=10.15
scene['geometry_source']='enclosure/r03/source/build_print_r03.py — identical tessellation to the R03 CAD previews'
scene.frame_set(982)
bpy.ops.file.pack_all()
bpy.ops.wm.save_as_mainfile(filepath=str(film/'assets/Yuhuan-X1-R03-CG.blend'))
if args.preview:
    preview.mkdir(parents=True,exist_ok=True)
    for seconds in [7.5,8.8,10.9,12,15.4,22.7,25,27.8,29.5,32.7]:
        scene.frame_set(round(seconds*30)+1)
        scene.render.filepath=str(preview/('cg-%.1f.png'%seconds))
        bpy.ops.render.render(write_still=True)
print('R03 MODEL REPLACED: exact CAD shell, 9.80 mm; actual Android UI packed.')

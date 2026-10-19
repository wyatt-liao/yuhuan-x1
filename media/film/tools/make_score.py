#!/usr/bin/env python3
"""Original 120 BPM electronic flash-film score. No sampled or licensed music."""
import argparse,wave
from pathlib import Path
import numpy as np
R=48000;D=35;BPM=120;BEAT=60/BPM
rng=np.random.default_rng(20261018)
track=np.zeros((R*D,2),np.float64)
def mix(at,s,gain=1,pan=0):
 i=max(0,int(at*R));s=np.asarray(s);n=min(len(s),len(track)-i)
 if n<=0:return
 s=s[:n]*gain
 if s.ndim==1:track[i:i+n,0]+=s*np.cos((pan+1)*np.pi/4);track[i:i+n,1]+=s*np.sin((pan+1)*np.pi/4)
 else:track[i:i+n]+=s

def noise(d,low=2000,high=15000):
 n=int(d*R);x=rng.normal(0,1,n);f=np.fft.rfftfreq(n,1/R);z=np.fft.rfft(x);m=(1-np.exp(-(f/low)**4))*np.exp(-(f/high)**6);s=np.fft.irfft(z*m,n);return s/(np.std(s)+1e-8)

def kick(at,g=.16):
 d=.37;t=np.arange(int(d*R))/R
 phase=2*np.pi*(49*t+(135-49)*(1-np.exp(-t*36))/36)
 s=np.sin(phase)*np.exp(-t*10)*np.minimum(1,t/.002)+.06*noise(d,2200,9000)*np.exp(-t*110)
 mix(at,s,g)
def hat(at,g=.025,open_=False):
 d=.20 if open_ else .07;t=np.arange(int(d*R))/R;s=noise(d,6500,15000)*np.exp(-t*(25 if open_ else 85))*np.minimum(1,t/.001);mix(at,s,g,.25 if open_ else -.2)
def snare(at,g=.065):
 d=.19;t=np.arange(int(d*R))/R;s=.75*noise(d,1300,9500)*np.exp(-t*28)+.25*np.sin(2*np.pi*190*t)*np.exp(-t*34);mix(at,s*np.minimum(1,t/.001),g)
def note(at,f,d=.25,g=.04,pan=0):
 t=np.arange(int(d*R))/R;e=np.minimum(1,t/.004)*np.exp(-t*13)*np.minimum(1,(d-t)/.04)
 s=np.sin(2*np.pi*f*t+.6*np.sin(2*np.pi*2*f*t)*np.exp(-t*16))+.16*np.sin(2*np.pi*f*2*t)
 mix(at,s*e,g,pan)
 for delay,amp,p in [(.1875,.22,-pan),(.375,.10,pan)]:mix(at+delay,s*e,g*amp,p)
def bass(at,f,d=.33,g=.075):
 t=np.arange(int(d*R))/R;s=np.sin(2*np.pi*f*t)+.22*np.sin(2*np.pi*2*f*t)+.08*np.sin(2*np.pi*3*f*t);e=np.minimum(1,t/.008)*np.minimum(1,(d-t)/.06)*np.exp(-t*2.4);mix(at,s*e,g)

# Small electronic hits under the opening questions.
for at in [0,1,2,3,4]:kick(at,.13);note(at+.04,[440,523.251,659.255,587.33,440][int(at)],.26,.036,(-1)**int(at)*.35)
for at in np.arange(.25,4.5,.5):hat(float(at),.015)
# Tight 120 BPM foundation; the two quiet breaks are deliberate editorial pauses.
for i in range(52):
 at=5+i*BEAT
 if at>=31:break
 gain=.19 if 8<=at<17 or 21<=at<27 else .13
 if 17<=at<21:gain=.075
 kick(at,gain)
 if i%2==1:snare(at,.068 if not 17<=at<21 else .03)
 hat(at+.25,.031 if at<17 or at>=21 else .012,open_=i%4==3)
 if (8<=at<17 or 21<=at<31) and i%2==0:hat(at+.375,.013)

# D minor / Bb / F / C, with a warm syncopated bass and precise glass plucks.
roots=[73.416,58.2705,87.307,65.4064]
scale=[293.6648,349.2282,440,523.2511,659.2551,523.2511,440,349.2282]
for section,begin,end in [('save',8,17),('phone',21,27),('craft',27,31)]:
 for i,at in enumerate(np.arange(begin,end,.25)):
  bar=int((at-begin)/2);root=roots[bar%4]
  if i%8 in [0,3,4,6]:bass(float(at),root*(1.5 if i%8==6 else 1),.25,.08)
  if i%4 in [0,2] or section=='phone':note(float(at),scale[(i+bar*2)%8]*(.5 if section=='craft' else 1),.32,.022 if section=='phone' else .017,(-1)**i*.48)

# Soft wide pads glue the cuts without smoothing away the rhythm.
for begin,end,notes in [(5,8,[146.832,220,329.628]),(8,17,[146.832,174.614,220,261.626]),(17,21,[130.813,195.998,293.665]),(21,27,[116.541,174.614,220,349.228]),(27,31,[146.832,220,329.628]),(31,35,[146.832,220,293.665,440])]:
 d=end-begin;t=np.arange(int(d*R))/R;e=np.minimum(1,t/.3)*np.minimum(1,(d-t)/.7)
 for k,f in enumerate(notes):
  phase=rng.uniform(0,6.28);s=np.column_stack([np.sin(2*np.pi*f*t+phase),np.sin(2*np.pi*(f+.11)*t+phase)])*e[:,None]
  mix(begin,s,.013 if begin<31 else .021)

# Directional transitions and the actual save button's timing in the film.
for at in [4.88,5.9,7.85,10.35,11.35,16.85,20.85,26.85,28.85,30.85]:
 d=.30;t=np.arange(int(d*R))/R;s=noise(d,900,10500)*np.sin(np.pi*t/d)**2
 mix(at,s,.026,-.45);mix(at+.018,s[::-1],.022,.45)
for at in [10.95,13.10,13.32,13.54]:
 d=.085;t=np.arange(int(d*R))/R;s=(np.sin(2*np.pi*1750*t)+.25*noise(d,4000,13000))*np.exp(-t*100)*np.minimum(1,t/.0008);mix(at,s,.10,.15)
for at,f in [(29,660),(30,660),(30.055,880),(30.11,1100),(30.165,1320)]:note(at,f,.17,.028,.2)
kick(31,.22);note(31.03,587.33,.70,.045,-.2);note(31.28,880,.9,.025,.4)

# Half a second of quiet sells the first pain. End with a clean reverberant tail.
time=np.arange(len(track))/R
mask=np.ones(len(track));mask[(time>=4.48)&(time<4.86)]=0
mask*=np.minimum(1,time/.025)*np.minimum(1,np.maximum(0,D-time)/.8)
track*=mask[:,None]
track=np.tanh(track*1.15);track*=.9/(np.max(np.abs(track))+1e-9)
a=argparse.ArgumentParser();a.add_argument('output',type=Path);args=a.parse_args();args.output.parent.mkdir(parents=True,exist_ok=True)
with wave.open(str(args.output),'wb') as f:f.setnchannels(2);f.setsampwidth(2);f.setframerate(R);f.writeframes((track*32767).astype('<i2').tobytes())
print(f'Original score: {D}s / {BPM} BPM / stereo / {R} Hz -> {args.output}')

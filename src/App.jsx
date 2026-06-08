import React, { useState, useEffect, useCallback, useRef, useMemo } from "react";

/* ══════════ CONFIG ══════════ */
const FB      = (import.meta.env.VITE_FB_DATABASE_URL||"").replace(/\/+$/,"");
const FB_KEY  = import.meta.env.VITE_FB_API_KEY||"";
const FB_PROJ  = import.meta.env.VITE_FB_PROJECT_ID||"";
const GAS     = import.meta.env.VITE_GAS_URL;
const IMGBB   = import.meta.env.VITE_IMGBB_API_KEY;
const SECRET  = import.meta.env.VITE_SECRET_KEY;

const C={bg:"#06080f",card:"#0c1220",border:"#16253d",accent:"#3b82f6",green:"#22c55e",red:"#ef4444",yellow:"#f59e0b",purple:"#8b5cf6",text:"#e2e8f0",muted:"#4b5e7a",panel:"#0e1a2e",navBg:"#080f1c"};

/* ══════════ ERROR BOUNDARY ══════════ */
class ErrorBoundary extends React.Component {
  constructor(p){super(p);this.state={err:null};}
  static getDerivedStateFromError(e){return{err:e};}
  componentDidCatch(e,info){console.error("App error:",e,info);}
  render(){
    if(this.state.err)return(
      <div style={{padding:32,color:"#ef4444",fontFamily:"monospace",background:"#06080f",minHeight:"100dvh"}}>
        <div style={{fontSize:28,marginBottom:12}}>⚠️ Error</div>
        <div style={{fontSize:12,marginBottom:8,color:"#e2e8f0"}}>{this.state.err?.message||"Unknown error"}</div>
        <button onClick={()=>this.setState({err:null})} style={{marginTop:16,padding:"8px 20px",background:"#3b82f6",color:"#fff",border:"none",borderRadius:8,cursor:"pointer"}}>রিলোড করুন</button>
      </div>
    );
    return this.props.children;
  }
}

/* ══════════ FIREBASE AUTH (email/password via REST) ══════════ */
let _idToken = null;
let _tokenExp = 0;

async function signInWithEmail(email, password) {
  const r = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${FB_KEY}`,
    {method:"POST",headers:{"Content-Type":"application/json"},
     body:JSON.stringify({email,password,returnSecureToken:true})}
  );
  const d = await r.json();
  if(!r.ok) throw new Error(d?.error?.message||"Login failed");
  _idToken = d.idToken;
  _tokenExp = Date.now() + (parseInt(d.expiresIn||3600)-60)*1000;
  localStorage.setItem("fb_email", email);
  localStorage.setItem("fb_pass_enc", btoa(password));
  return d;
}

async function refreshTokenIfNeeded() {
  if(_idToken && Date.now() < _tokenExp) return _idToken;
  const email = localStorage.getItem("fb_email");
  const passEnc = localStorage.getItem("fb_pass_enc");
  if(email && passEnc){
    try{ await signInWithEmail(email, atob(passEnc)); return _idToken; }
    catch(e){ _idToken=null; return null; }
  }
  return null;
}

function _authQ(token){ return token ? `?auth=${token}` : ""; }

/* ══════════ FIREBASE REST ══════════ */
async function _checkResp(r){
  if(!r.ok){
    let msg=`HTTP ${r.status}`;
    try{const j=await r.json();msg=j?.error||msg;}catch(_){}
    throw new Error(msg);
  }
  return r.json();
}
const _tok=()=>refreshTokenIfNeeded();
const fbGet   = async p=>{const t=await _tok();const r=await fetch(`${FB}/${p}.json${_authQ(t)}`);return r.json();};
const fbPatch  = async(p,d)=>{const t=await _tok();const r=await fetch(`${FB}/${p}.json${_authQ(t)}`,{method:"PATCH",headers:{"Content-Type":"application/json"},body:JSON.stringify(d)});return _checkResp(r);};
const fbSet   = async(p,d)=>{const t=await _tok();const r=await fetch(`${FB}/${p}.json${_authQ(t)}`,{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify(d)});return _checkResp(r);};
const fbPush  = async(p,d)=>{const t=await _tok();const r=await fetch(`${FB}/${p}.json${_authQ(t)}`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(d)});return _checkResp(r);};
const fbDelete= async p=>{const t=await _tok();const r=await fetch(`${FB}/${p}.json${_authQ(t)}`,{method:"DELETE"});return _checkResp(r);};


/* ══════════ GAS helpers ══════════ */
const gasBg  = params=>setTimeout(()=>fetch(GAS+"?"+new URLSearchParams({...params,secret:SECRET})).catch(()=>{}),300);
const gasPost= body  =>setTimeout(()=>fetch(GAS,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({...body,secret:SECRET})}).catch(()=>{}),300);
const gasCall= async params=>{const r=await fetch(GAS+"?"+new URLSearchParams({...params,secret:SECRET}));return r.json();};

/* ══════════ SIMPLE FETCH CACHE — no subscriptions, no loops ══════════ */
const _store = {}; // path -> {data, ts, promise}
const STALE  = 90_000; // 90s

async function loadPath(path, force=false){
  const now = Date.now();
  const cached = _store[path];
  if(!force && cached && !cached.promise && now - cached.ts < STALE) return cached.data;
  if(cached?.promise) return cached.promise;
  const p = fbGet(path).then(data=>{
    _store[path] = {data, ts:Date.now(), promise:null};
    return data;
  }).catch(e=>{
    if(_store[path]) _store[path].promise = null;
    throw e;
  });
  if(!_store[path]) _store[path]={data:null,ts:0,promise:null};
  _store[path].promise = p;
  return p;
}

function invalidate(...paths){ paths.forEach(p=>{if(_store[p])_store[p].ts=0;}); }
function invalidateAll(){ Object.keys(_store).forEach(p=>{if(_store[p])_store[p].ts=0;}); }

/* Simple hook — fetches once, no subscription loop */
function useFB(path, tick=0){
  const [state, setState] = useState(()=>{
    const cached = _store[path];
    return {data: cached?.data??null, loading: !cached?.data};
  });
  const lastTick = useRef(-1);
  const lastPath = useRef(null);

  useEffect(()=>{
    if(!path) return;
    const force = tick !== lastTick.current || path !== lastPath.current;
    lastTick.current = tick;
    lastPath.current = path;

    const cached = _store[path];
    if(!force && cached?.data && Date.now()-cached.ts < STALE){
      setState({data:cached.data, loading:false});
      return;
    }

    let cancelled = false;
    setState(s=>({...s, loading:!s.data}));
    loadPath(path, force).then(data=>{
      if(!cancelled) setState({data, loading:false});
    }).catch(()=>{
      if(!cancelled) setState(s=>({...s, loading:false}));
    });
    return ()=>{ cancelled=true; };
  }, [path, tick]);

  return state;
}

/* ══════════ HELPERS ══════════ */
const fmt=n=>(n||0).toLocaleString();
const pct=(a,b)=>b?Math.round(a/b*100):0;
const initials=n=>(n||"?").split(" ").map(w=>w[0]).join("").toUpperCase().slice(0,2);
const nowTs=()=>new Date().toLocaleString("bn-BD",{timeZone:"Asia/Dhaka"});
const timeAgo=ts=>{
  if(!ts)return"—";
  try{
    const d=new Date(ts.replace?ts.replace(/(\d{2})-(\d{2})-(\d{4})/,"$3-$2-$1"):ts);
    const s=Date.now()-d.getTime();
    if(s<60000)return"এখনই";
    if(s<3600000)return~~(s/60000)+"মি আগে";
    if(s<86400000)return~~(s/3600000)+"ঘণ্টা আগে";
    return~~(s/86400000)+"দিন আগে";
  }catch{return ts;}
};
const toArr=raw=>{
  if(!raw)return[];
  if(Array.isArray(raw))return raw.filter(Boolean);
  return Object.entries(raw).map(([k,v])=>v?{...v,_fbKey:k}:null).filter(Boolean);
};
const phoneKey=ph=>(ph||"").replace(/^'+/,"").trim().replace(/[.#$\[\]\s]/g,"_");
const matchPhone=(key,phone)=>{
  const k=key.replace(/_/g,"");
  const p=(phone||"").replace(/[.#$\[\]\s]/g,"");
  return k===p||k===p.replace(/^0+/,"")||k.replace(/^0+/,"")===p.replace(/^0+/,"");
};
const uploadImg=async file=>{
  const fd=new FormData();fd.append("image",file);
  const r=await fetch(`https://api.imgbb.com/1/upload?key=${IMGBB}`,{method:"POST",body:fd});
  return(await r.json())?.data?.url||"";
};

/* ══════════ MODAL BACK HANDLER ══════════ */
// যেকোনো modal এ এটা call করলে Android back button এ modal বন্ধ হবে
function useModalBack(onClose){
  useEffect(()=>{
    window.dispatchEvent(new Event("modal-open"));
    const handler=()=>onClose();
    window.addEventListener("back-press",handler);
    return()=>{
      window.dispatchEvent(new Event("modal-close"));
      window.removeEventListener("back-press",handler);
    };
  },[]);// eslint-disable-line
}

/* ══════════ TOAST ══════════ */
let _tid=0;
function useToasts(){
  const[t,set]=useState([]);
  const push=useCallback((type,title,msg="")=>{
    const id=++_tid;
    set(p=>[...p.slice(-4),{id,type,title,msg}]);
    setTimeout(()=>set(p=>p.filter(x=>x.id!==id)),4000);
  },[]);
  return[t,push];
}
function Toasts({t}){return(
  <div className="toasts">
    {t.map(x=>(
      <div key={x.id} className={`toast ${x.type}`}>
        <div className="t-icon">{x.type==="success"?"✅":x.type==="error"?"❌":x.type==="warn"?"⚠️":"ℹ️"}</div>
        <div className="t-body"><div className="t-title">{x.title}</div>{x.msg&&<div className="t-msg">{x.msg}</div>}</div>
      </div>
    ))}
  </div>
);}

/* ══════════ DELETE WARNING MODAL ══════════ */
function DeleteWarningModal({title,description,onConfirm,onCancel,loading}){
  useModalBack(onCancel);
  return(
    <div className="ovl" style={{zIndex:300}}>
      <div className="modal" style={{borderTop:`3px solid ${C.red}`}}>
        <div className="mh"/>
        <div style={{textAlign:"center",marginBottom:16}}>
          <div style={{fontSize:40,marginBottom:8}}>🗑️</div>
          <div style={{fontSize:16,fontWeight:700,color:C.red,marginBottom:6}}>{title}</div>
          <div style={{fontSize:12,color:C.muted,lineHeight:1.6}}>{description}</div>
        </div>
        <div style={{background:"#ef444412",border:"1px solid #ef444430",borderRadius:10,padding:"10px 12px",marginBottom:14,fontSize:11,color:C.red,textAlign:"center",fontWeight:600}}>
          ⚠️ এই কাজ পূর্বাবস্থায় ফেরানো যাবে না!
        </div>
        <div style={{display:"flex",gap:8}}>
          <button className="btn bg" style={{flex:1,justifyContent:"center"}} onClick={onCancel} disabled={loading}>বাতিল</button>
          <button className="btn" style={{flex:2,justifyContent:"center",background:C.red,color:"#fff"}} onClick={onConfirm} disabled={loading}>
            {loading?"⏳ ডিলিট হচ্ছে...":"🗑️ হ্যাঁ, ডিলিট করুন"}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ══════════ CSS ══════════ */
const css=`
@import url('https://fonts.googleapis.com/css2?family=Noto+Sans+Bengali:wght@400;500;600;700&family=Space+Grotesk:wght@400;500;600;700&display=swap');
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
html,body,#root{background:${C.bg};color:${C.text};font-family:'Noto Sans Bengali','Space Grotesk',sans-serif;min-height:100dvh;max-width:480px;margin:0 auto;overflow-x:hidden}
::-webkit-scrollbar{width:3px}::-webkit-scrollbar-thumb{background:${C.border};border-radius:10px}
.bottom-nav{position:fixed;bottom:0;left:50%;transform:translateX(-50%);width:100%;max-width:480px;background:${C.navBg};border-top:1px solid ${C.border};display:flex;z-index:100;padding-bottom:env(safe-area-inset-bottom,8px)}
.nav-btn{flex:1;display:flex;flex-direction:column;align-items:center;gap:2px;padding:8px 2px 6px;cursor:pointer;border:none;background:transparent;color:${C.muted};font-family:inherit;font-size:9px;font-weight:500;transition:color .15s;position:relative}
.nav-btn.active{color:${C.accent}}.nav-icon{font-size:18px;line-height:1}
.nav-badge{position:absolute;top:5px;right:calc(50% - 16px);background:${C.red};color:#fff;font-size:8px;font-weight:700;width:14px;height:14px;border-radius:50%;display:flex;align-items:center;justify-content:center}
.topbar{background:${C.card};border-bottom:1px solid ${C.border};padding:12px 16px 10px;display:flex;align-items:center;justify-content:space-between;position:sticky;top:0;z-index:50}
.topbar-title{font-size:15px;font-weight:700}.topbar-sub{font-size:10px;color:${C.muted};margin-top:1px}
.icon-btn{width:34px;height:34px;border-radius:9px;background:${C.panel};border:1px solid ${C.border};color:${C.text};font-size:16px;display:flex;align-items:center;justify-content:center;cursor:pointer}
.icon-btn.spin{animation:spin 1s linear infinite}
.page{padding:13px;padding-bottom:82px;min-height:100dvh}
.sg{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:10px}
.sc{background:${C.card};border:1px solid ${C.border};border-radius:13px;padding:12px;position:relative;overflow:hidden}
.sc::after{content:attr(data-icon);position:absolute;right:8px;bottom:6px;font-size:24px;opacity:.12}
.sl{font-size:10px;color:${C.muted};font-weight:600;margin-bottom:4px;text-transform:uppercase;letter-spacing:.5px}
.sv{font-size:24px;font-weight:700;line-height:1}
.sv-b{color:${C.accent}}.sv-g{color:${C.green}}.sv-r{color:${C.red}}.sv-y{color:${C.yellow}}.sv-p{color:${C.purple}}
.tb{border-top:2px solid ${C.accent}}.tg{border-top:2px solid ${C.green}}.tr{border-top:2px solid ${C.red}}.ty{border-top:2px solid ${C.yellow}}.tp{border-top:2px solid ${C.purple}}
.card{background:${C.card};border:1px solid ${C.border};border-radius:13px;padding:13px;margin-bottom:10px}
.ct{font-size:11px;font-weight:700;color:${C.muted};text-transform:uppercase;letter-spacing:.8px;margin-bottom:11px}
.av{width:38px;height:38px;border-radius:50%;background:linear-gradient(135deg,${C.accent},${C.purple});display:flex;align-items:center;justify-content:center;font-size:13px;font-weight:700;color:#fff;flex-shrink:0}
.av.sm{width:30px;height:30px;font-size:11px}
.pill{display:inline-flex;align-items:center;padding:2px 8px;border-radius:20px;font-size:10px;font-weight:700;white-space:nowrap;flex-shrink:0}
.pa{background:#22c55e18;color:${C.green};border:1px solid #22c55e33}
.pi{background:#ef444418;color:${C.red};border:1px solid #ef444433}
.pp{background:#f59e0b18;color:${C.yellow};border:1px solid #f59e0b33}
.btn{display:inline-flex;align-items:center;gap:4px;padding:7px 12px;border-radius:9px;font-size:12px;font-weight:600;font-family:inherit;cursor:pointer;border:none;transition:all .15s;white-space:nowrap}
.btn:active{transform:scale(.96)}.btn:disabled{opacity:.45;pointer-events:none}
.bp{background:${C.accent};color:#fff}.bs{background:#22c55e20;color:${C.green};border:1px solid #22c55e40}
.bg{background:transparent;color:${C.muted};border:1px solid ${C.border}}.bg:hover{background:${C.border};color:${C.text}}
.bb{width:100%;justify-content:center;padding:10px}
.inp,.ta{background:${C.panel};border:1px solid ${C.border};border-radius:9px;padding:9px 12px;color:${C.text};font-family:inherit;font-size:13px;width:100%;outline:none;transition:border-color .2s;-webkit-appearance:none}
.inp:focus,.ta:focus{border-color:${C.accent}}.inp::placeholder,.ta::placeholder{color:${C.muted}}
.ta{resize:vertical;min-height:75px}.fld{margin-bottom:10px}
.fld label{display:block;font-size:10px;font-weight:700;color:${C.muted};letter-spacing:.8px;margin-bottom:4px;text-transform:uppercase}
.sw{position:relative;margin-bottom:10px}.sw .si{position:absolute;left:10px;top:50%;transform:translateY(-50%);font-size:14px;pointer-events:none}.sw .inp{padding-left:32px}
.ftabs{display:flex;gap:5px;margin-bottom:11px;overflow-x:auto;padding-bottom:2px;scrollbar-width:none}.ftabs::-webkit-scrollbar{display:none}
.ftab{flex-shrink:0;padding:6px 12px;border-radius:20px;font-size:11px;font-weight:600;cursor:pointer;border:1px solid ${C.border};background:transparent;color:${C.muted};font-family:inherit;transition:all .15s}
.ftab.on{background:${C.accent};color:#fff;border-color:${C.accent}}
.rc{background:${C.panel};border:1px solid ${C.border};border-radius:11px;padding:11px;margin-bottom:8px}
.ri{font-size:12px;color:${C.text};line-height:1.5;background:${C.card};border-radius:7px;padding:7px 9px;margin-top:7px;border-left:2px solid ${C.red}}
.rm{font-size:10px;color:${C.muted};margin-top:4px;display:flex;gap:6px;flex-wrap:wrap}
.ovl{position:fixed;inset:0;background:#00000094;z-index:200;display:flex;align-items:flex-end}
.modal{background:${C.card};border:1px solid ${C.border};border-radius:20px 20px 0 0;padding:16px 16px 36px;width:100%;max-height:88dvh;overflow-y:auto;animation:su .22s ease}
.mh{width:32px;height:4px;background:${C.border};border-radius:4px;margin:0 auto 13px}
.mt{font-size:15px;font-weight:700;margin-bottom:13px}
.fs{position:fixed;inset:0;background:${C.bg};z-index:150;overflow-y:auto}
.fsh{background:${C.card};border-bottom:1px solid ${C.border};padding:12px 14px;display:flex;align-items:center;gap:11px;position:sticky;top:0;z-index:10}
.bk{width:32px;height:32px;border-radius:8px;background:${C.panel};border:1px solid ${C.border};color:${C.text};font-size:15px;display:flex;align-items:center;justify-content:center;cursor:pointer;flex-shrink:0}
.sk{background:linear-gradient(90deg,${C.border},#1a2840,${C.border});background-size:200% 100%;animation:shim 1.4s infinite;border-radius:9px;height:64px;margin-bottom:8px}
.empty{text-align:center;padding:40px 20px;color:${C.muted}}.ei{font-size:36px;margin-bottom:8px;opacity:.25}
.toasts{position:fixed;top:13px;left:50%;transform:translateX(-50%);width:calc(100% - 26px);max-width:440px;z-index:999;display:flex;flex-direction:column;gap:6px;pointer-events:none}
.toast{background:${C.card};border:1px solid ${C.border};border-radius:11px;padding:10px 12px;display:flex;gap:8px;align-items:flex-start;animation:ti .25s ease;box-shadow:0 8px 28px #00000080;pointer-events:all}
.toast.success{border-left:3px solid ${C.green}}.toast.error{border-left:3px solid ${C.red}}.toast.warn{border-left:3px solid ${C.yellow}}.toast.info{border-left:3px solid ${C.accent}}
.t-icon{font-size:16px}.t-body{flex:1}.t-title{font-size:12px;font-weight:700}.t-msg{font-size:11px;color:${C.muted};margin-top:1px}
.atabs{display:flex;background:${C.panel};border-radius:10px;padding:3px;margin-bottom:11px;gap:3px}
.atab{flex:1;text-align:center;padding:7px 3px;border-radius:7px;font-size:11px;font-weight:600;cursor:pointer;border:none;background:transparent;color:${C.muted};font-family:inherit;transition:all .2s}
.atab.on{background:${C.card};color:${C.text};box-shadow:0 2px 6px #00000040}
.srow{display:flex;align-items:center;justify-content:space-between;padding:8px 0;border-bottom:1px solid ${C.border}40;font-size:12px}.srow:last-child{border-bottom:none}
.sbar{height:3px;border-radius:3px;background:${C.border};flex:1;margin:3px 6px 0 0;overflow:hidden}.sbar-f{height:100%;border-radius:3px;transition:width .6s ease}
.slb{font-size:10px;font-weight:700;color:${C.muted};letter-spacing:1.2px;text-transform:uppercase;margin:14px 0 8px}
.nr{display:flex;gap:8px;align-items:flex-start;padding:9px 0;border-bottom:1px solid ${C.border}40}.nr:last-child{border-bottom:none}
.nd{width:7px;height:7px;border-radius:50%;margin-top:4px;flex-shrink:0}.nd.n{background:${C.accent}}.nd.o{background:${C.muted}}
.nc{flex:1}.nt{font-size:12px;font-weight:600}.ns{font-size:11px;color:${C.muted};margin-top:1px}.ntm{font-size:10px;color:${C.muted};white-space:nowrap}
.steps{display:flex;margin-bottom:16px}.step{flex:1;text-align:center;font-size:10px;font-weight:700;padding:5px 2px;border-bottom:2px solid ${C.border};color:${C.muted};transition:all .2s}
.step.done{border-color:${C.green};color:${C.green}}.step.act{border-color:${C.accent};color:${C.accent}}
.bc{display:flex;align-items:flex-end;gap:2px;height:64px;margin-top:5px}
.bcol{display:flex;flex-direction:column;align-items:center;flex:1;gap:2px}
.brect{width:100%;border-radius:3px 3px 0 0;min-height:2px}.blbl{font-size:7px;color:${C.muted};white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:26px;text-align:center}
.sri{display:flex;align-items:center;gap:9px;padding:9px;background:${C.panel};border:1px solid ${C.border};border-radius:10px;margin-bottom:6px;cursor:pointer;transition:border-color .15s}.sri:hover{border-color:${C.accent}}
.stag{font-size:9px;font-weight:700;padding:2px 6px;border-radius:7px;background:${C.accent}20;color:${C.accent};flex-shrink:0}
.rw{position:relative;width:68px;height:68px;flex-shrink:0}.rw svg{transform:rotate(-90deg)}
.rpct{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-size:13px;font-weight:700}
.tp2{padding:6px 14px;border-radius:20px;font-size:11px;font-weight:700;cursor:pointer;border:1px solid ${C.border};background:transparent;color:${C.muted};font-family:inherit;transition:all .15s;flex-shrink:0}
.tp2.on{background:${C.accent};color:#fff;border-color:${C.accent}}
.cc{padding:4px 10px;border-radius:8px;font-size:11px;font-weight:700;cursor:pointer;border:1px solid ${C.border};background:transparent;color:${C.muted};font-family:inherit;transition:all .15s;white-space:nowrap}
.cc.on{background:${C.green}20;color:${C.green};border-color:${C.green}40}
.qcard{background:${C.panel};border:1px solid ${C.border};border-radius:11px;padding:11px;margin-bottom:8px}
.qcard-q{font-size:12px;font-weight:600;line-height:1.5;margin-bottom:7px;color:${C.text}}
.qcard-meta{display:flex;gap:5px;flex-wrap:wrap;align-items:center}
.qtag{font-size:9px;font-weight:700;padding:2px 7px;border-radius:6px;white-space:nowrap}
.qtag-mcq{background:${C.accent}20;color:${C.accent};border:1px solid ${C.accent}30}
.qtag-wr{background:${C.purple}20;color:${C.purple};border:1px solid ${C.purple}30}
.qtag-sub{background:${C.green}15;color:${C.green};border:1px solid ${C.green}25}
.qtag-tp{background:${C.yellow}15;color:${C.yellow};border:1px solid ${C.yellow}25}
.rename-row{display:flex;align-items:center;gap:8px;padding:10px;background:${C.panel};border:1px solid ${C.border};border-radius:10px;margin-bottom:7px}
.rename-name{flex:1;font-size:12px;font-weight:600}
.rename-count{font-size:10px;color:${C.muted};white-space:nowrap}
@keyframes spin{to{transform:rotate(360deg)}}
@keyframes su{from{transform:translateY(36px);opacity:0}to{transform:translateY(0);opacity:1}}
@keyframes shim{0%{background-position:-200% 0}100%{background-position:200% 0}}
@keyframes ti{from{transform:translateY(-16px);opacity:0}to{transform:translateY(0);opacity:1}}
`;

/* ══════════ MINI COMPONENTS ══════════ */
function Ring({val,max,color}){
  const r=26,c2=2*Math.PI*r,p=max?Math.min(100,Math.round(val/max*100)):0;
  return(
    <div className="rw">
      <svg width="68" height="68" viewBox="0 0 68 68">
        <circle cx="34" cy="34" r={r} fill="none" stroke={C.border} strokeWidth="6"/>
        <circle cx="34" cy="34" r={r} fill="none" stroke={color} strokeWidth="6"
          strokeDasharray={`${c2*p/100} ${c2}`} strokeLinecap="round"/>
      </svg>
      <div className="rpct" style={{color}}>{p}%</div>
    </div>
  );
}

function Bar({data,color}){
  if(!data?.length)return null;
  const mx=Math.max(...data.map(d=>d.v),1);
  return(
    <div className="bc">
      {data.map((d,i)=>(
        <div key={i} className="bcol">
          <div className="brect" style={{height:(d.v/mx*58)+"px",background:color,opacity:.85}}/>
          <div className="blbl">{d.l}</div>
        </div>
      ))}
    </div>
  );
}

/* ── Tree: memoised, renders only when data changes ── */
const Tree = React.memo(function Tree({entries,total,color}){
  const[open,setO]=useState({});
  const tog=useCallback(k=>setO(p=>({...p,[k]:!p[k]})),[]);
  return(
    <>
      {entries.map(([sub,v])=>{
        const tops=Object.entries(v.topics||{});
        return(
          <div key={sub} style={{marginBottom:7}}>
            <div style={{display:"flex",alignItems:"center",padding:"8px 0",borderBottom:`1px solid ${C.border}40`,cursor:tops.length?"pointer":"default"}} onClick={()=>tops.length&&tog(sub)}>
              <div style={{flex:1}}>
                <div style={{fontWeight:700,fontSize:12,display:"flex",alignItems:"center",gap:4}}>
                  {tops.length>0&&<span style={{fontSize:9,color:C.muted,display:"inline-block",transform:open[sub]?"rotate(90deg)":"none",transition:"transform .2s"}}>▶</span>}
                  {sub}
                </div>
                <div style={{display:"flex",alignItems:"center",marginTop:3}}><div className="sbar"><div className="sbar-f" style={{width:pct(v.total,total)+"%",background:color}}/></div></div>
                <div style={{fontSize:9,color:C.muted,marginTop:1}}>MCQ:{v.mcq||0} · Written:{v.written||0}{tops.length?` · ${tops.length}টি Topic`:""}</div>
              </div>
              <div style={{fontWeight:700,color,fontSize:16,minWidth:32,textAlign:"right"}}>{v.total}</div>
            </div>
            {open[sub]&&tops.map(([tp,tv])=>{
              const sts=Object.entries(tv.subtopics||{});
              const tk=sub+"_"+tp;
              return(
                <div key={tp} style={{marginLeft:12,borderLeft:`2px solid ${color}30`}}>
                  <div style={{display:"flex",alignItems:"center",padding:"6px 0 6px 9px",cursor:sts.length?"pointer":"default"}} onClick={()=>sts.length&&tog(tk)}>
                    <div style={{flex:1,fontSize:11,fontWeight:600,display:"flex",alignItems:"center",gap:3}}>
                      {sts.length>0&&<span style={{fontSize:8,color:C.muted,display:"inline-block",transform:open[tk]?"rotate(90deg)":"none",transition:"transform .2s"}}>▶</span>}
                      📂 {tp}
                    </div>
                    <div style={{fontWeight:700,color,fontSize:13,minWidth:28,textAlign:"right"}}>{tv.total}</div>
                  </div>
                  {open[tk]&&sts.map(([st,sv])=>(
                    <div key={st} style={{display:"flex",alignItems:"center",padding:"5px 0 5px 18px",borderBottom:`1px solid ${C.border}20`}}>
                      <div style={{flex:1}}><div style={{fontSize:10}}>📄 {st}</div><div style={{fontSize:9,color:C.muted}}>MCQ:{sv.mcq||0} · Written:{sv.written||0}</div></div>
                      <div style={{fontWeight:600,color:C.muted,fontSize:12,minWidth:26,textAlign:"right"}}>{sv.total}</div>
                    </div>
                  ))}
                </div>
              );
            })}
          </div>
        );
      })}
    </>
  );
});

/* ── buildSubjectMap: heavy — called only when raw data changes ── */
function buildSubjectMap(arr){
  const map={};
  for(let i=0;i<arr.length;i++){
    const q=arr[i];
    const sub=(q.Subject||q.subject||"Unknown").trim();
    const typ=(q.QType||q.qtype||"MCQ").toLowerCase();
    const st=(q.Sub_topic||q.sub_topic||"General").trim();
    const parts=st.includes(" > ")?st.split(" > "):[st,st];
    const top=parts[0].trim()||"General";
    const stF=parts.length>1?parts[1].trim():st;
    const isWr=typ==="written";
    if(!map[sub])map[sub]={total:0,mcq:0,written:0,topics:{}};
    map[sub].total++;if(isWr)map[sub].written++;else map[sub].mcq++;
    if(!map[sub].topics[top])map[sub].topics[top]={total:0,subtopics:{}};
    map[sub].topics[top].total++;
    if(!map[sub].topics[top].subtopics[stF])map[sub].topics[top].subtopics[stF]={total:0,mcq:0,written:0};
    map[sub].topics[top].subtopics[stF].total++;
    if(isWr)map[sub].topics[top].subtopics[stF].written++;else map[sub].topics[top].subtopics[stF].mcq++;
  }
  return map;
}

/* ══════════ DASHBOARD ══════════ */
function DashboardPage({push,tick}){
  const{data:users,loading:uL}  = useFB("Users",tick);
  const{data:qbank}             = useFB("QBank",tick);
  const{data:study}             = useFB("Study",tick);
  const{data:reports}           = useFB("Reports",tick);
  // Quiz loaded lazily — not blocking
  const[quizData,setQuizData]   = useState(null);
  const[atab,setAtab]           = useState("qbank");

  // load quiz only when analytics tab selected
  useEffect(()=>{
    if(atab==="quiz"&&!quizData){
      loadPath("Quiz").then(d=>setQuizData(d)).catch(()=>{});
    }
  },[atab,quizData]);

  const userArr = useMemo(()=>toArr(users),[users]);
  const total   = userArr.length;
  const active  = useMemo(()=>userArr.filter(u=>(u.Status||u.status||"").toLowerCase()==="active").length,[userArr]);

  const qbankArr   = useMemo(()=>toArr(qbank),[qbank]);
  const qbT        = qbankArr.length;
  const stT        = useMemo(()=>toArr(study).length,[study]);
  const rptT       = useMemo(()=>toArr(reports).length,[reports]);
  const quizT      = useMemo(()=>toArr(quizData).length,[quizData]);

  const qbankMap   = useMemo(()=>buildSubjectMap(qbankArr),[qbankArr]);
  const quizMap    = useMemo(()=>buildSubjectMap(toArr(quizData)),[quizData]);
  const qbankEntries = useMemo(()=>Object.entries(qbankMap),[qbankMap]);
  const quizEntries  = useMemo(()=>Object.entries(quizMap),[quizMap]);

  const days = useMemo(()=>[...Array(7)].map((_,i)=>{
    const d=new Date();d.setDate(d.getDate()-(6-i));
    return{l:`${d.getDate()}/${d.getMonth()+1}`,v:0};
  }),[]);

  return(
    <div className="page">
      <div className="sg">
        <div className="sc tb" data-icon="👥"><div className="sl">স্টুডেন্ট</div><div className="sv sv-b">{fmt(total)}</div></div>
        <div className="sc tg" data-icon="✅"><div className="sl">অ্যাক্টিভ</div><div className="sv sv-g">{fmt(active)}</div></div>
        <div className="sc ty" data-icon="⏳"><div className="sl">পেন্ডিং</div><div className="sv sv-y">{fmt(total-active)}</div></div>
        <div className="sc tr" data-icon="🚨"><div className="sl">রিপোর্ট</div><div className="sv sv-r">{fmt(rptT)}</div></div>
      </div>
      <div className="sg">
        <div className="sc tb" data-icon="❓"><div className="sl">Quiz</div><div className="sv sv-b">{uL?"…":fmt(quizT)}</div></div>
        <div className="sc tg" data-icon="📚"><div className="sl">QBank</div><div className="sv sv-g">{fmt(qbT)}</div></div>
        <div className="sc ty" data-icon="📖"><div className="sl">Study</div><div className="sv sv-y">{fmt(stT)}</div></div>
        <div className="sc tp" data-icon="📊"><div className="sl">মোট</div><div className="sv sv-p">{fmt(quizT+qbT+stT)}</div></div>
      </div>
      <div className="card">
        <div className="ct">📈 Daily Active (৭ দিন)</div>
        <Bar data={days} color={C.accent}/>
      </div>
      <div className="card">
        <div className="ct">📊 Analytics</div>
        <div className="atabs">
          {[["qbank","📚 QBank"],["quiz","❓ Quiz"],["study","📖 Study"]].map(([v,l])=>(
            <button key={v} className={`atab${atab===v?" on":""}`} onClick={()=>setAtab(v)}>{l}</button>
          ))}
        </div>
        {atab==="qbank"&&(qbankEntries.length===0
          ?<div style={{textAlign:"center",color:C.muted,padding:"12px 0",fontSize:12}}>{!qbank?"⏳ লোড হচ্ছে...":"ডেটা নেই"}</div>
          :<Tree entries={qbankEntries} total={qbT} color={C.green}/>
        )}
        {atab==="quiz"&&(quizEntries.length===0
          ?<div style={{textAlign:"center",color:C.muted,padding:"12px 0",fontSize:12}}>{!quizData?"⏳ লোড হচ্ছে...":"ডেটা নেই"}</div>
          :<Tree entries={quizEntries} total={quizT} color={C.accent}/>
        )}
        {atab==="study"&&<div style={{textAlign:"center",color:C.muted,padding:"12px 0",fontSize:12}}>{fmt(stT)}টি নোট</div>}
      </div>
    </div>
  );
}

/* ══════════ SIGNUPS ══════════ */
function SignupsPage({push,tick}){
  const{data:usersRaw,loading}=useFB("Users",tick);
  const[activating,setActivating]=useState(null);
  const[done,setDone]=useState(new Set());

  const rows=useMemo(()=>toArr(usersRaw).filter(u=>{
    const st=(u.Status||u.status||"").toLowerCase();
    const id=u._fbKey||(u.Phone||u.phone||"");
    return(st==="inactive"||st===""||st==="pending")&&!done.has(id);
  }),[usersRaw,done]);

  const activate=async u=>{
    const phone=u.Phone||u.phone||"";
    const fkey=u._fbKey||phoneKey(phone);
    setActivating(fkey);
    try{
      await fbPatch(`Users/${fkey}`,{Status:"Active"});
      await fbSet(`Notifications/${fkey}/welcome_${Date.now()}`,{type:"welcome",title:"🎉 অ্যাকাউন্ট অ্যাক্টিভ!",body:"Smart Study-তে স্বাগতম!",time:nowTs(),read:false});
      gasBg({action:"activateUser",phone});
      push("success","✅ অ্যাক্টিভ!",u.Name||u.name||phone);
      setDone(p=>new Set([...p,fkey]));
      invalidate("Users");
    }catch(e){push("error","ব্যর্থ",e.message);}
    setActivating(null);
  };

  return(
    <div className="page">
      <div style={{background:"#ef444412",border:"1px solid #ef444430",borderRadius:10,padding:"8px 12px",fontSize:12,color:C.red,fontWeight:600,marginBottom:11,display:"flex",alignItems:"center",justifyContent:"space-between"}}>
        <span>🔔 {rows.length}টি পেন্ডিং</span>
        {loading&&<span style={{fontSize:10,color:C.muted}}>⏳</span>}
      </div>
      {loading&&!usersRaw?[...Array(3)].map((_,i)=><div key={i} className="sk"/>):
       rows.length===0?<div className="empty"><div className="ei">🎉</div><p>সব অ্যাক্টিভ!</p></div>:
       rows.map((u,i)=>{
        const nm=u.Name||u.name||"অজানা",ph=u.Phone||u.phone||"—";
        const fkey=u._fbKey||phoneKey(ph);
        return(
          <div key={i} className="card" style={{padding:12}}>
            <div style={{display:"flex",alignItems:"center",gap:10,marginBottom:10}}>
              <div className="av">{initials(nm)}</div>
              <div style={{flex:1,minWidth:0}}>
                <div style={{fontWeight:700,fontSize:13}}>{nm}</div>
                <div style={{fontSize:11,color:C.muted}}>📱 {ph}</div>
                {(u.Email||u.email)&&<div style={{fontSize:11,color:C.muted}}>✉️ {u.Email||u.email}</div>}
                <div style={{fontSize:10,color:C.muted}}>🕐 {timeAgo(u.Timestamp||u.createdAt)}</div>
              </div>
              <span className="pill pp">⏳ পেন্ডিং</span>
            </div>
            <button className="btn bs bb" disabled={!!activating} onClick={()=>activate(u)}>
              {activating===fkey?"⏳ হচ্ছে...":"✅ অ্যাক্টিভ করুন"}
            </button>
          </div>
        );
       })
      }
    </div>
  );
}

/* ══════════ STUDENTS ══════════ */
function StudentsPage({push,tick}){
  const{data:usersRaw,loading}=useFB("Users",tick);
  const[search,setSrc]=useState("");
  const[tab,setTab]=useState("all");
  const[detail,setDetail]=useState(null);
  const[notify,setNotify]=useState(null);
  const[busy,setBusy]=useState(null);

  const users=useMemo(()=>toArr(usersRaw),[usersRaw]);

  const filtered=useMemo(()=>{
    const q=search.toLowerCase();
    return users.filter(u=>{
      const nm=(u.Name||u.name||"").toLowerCase();
      const ph=(u.Phone||u.phone||"").toLowerCase();
      const st=(u.Status||"").toLowerCase();
      return(!q||nm.includes(q)||ph.includes(q))&&(tab==="all"||st===tab);
    });
  },[users,search,tab]);

  const activate=async u=>{
    const phone=u.Phone||u.phone||"";
    const fkey=u._fbKey||phoneKey(phone);
    setBusy(fkey);
    try{
      await fbPatch(`Users/${fkey}`,{Status:"Active"});
      invalidate("Users");
      gasBg({action:"activateUser",phone});
      push("success","✅ অ্যাক্টিভ!",u.Name||u.name);
    }catch(e){push("error","ব্যর্থ",e.message);}
    setBusy(null);
  };

  if(detail)return<StudentDetail user={detail} onBack={()=>setDetail(null)} push={push}/>;

  return(
    <div className="page">
      <div className="sw"><span className="si">🔍</span><input className="inp" placeholder="নাম বা ফোন..." value={search} onChange={e=>setSrc(e.target.value)}/></div>
      <div className="ftabs">
        {[["all","সবাই"],["active","✅ অ্যাক্টিভ"],["inactive","🔴 ইনঅ্যাক্টিভ"]].map(([v,l])=>(
          <button key={v} className={`ftab${tab===v?" on":""}`} onClick={()=>setTab(v)}>{l}</button>
        ))}
      </div>
      <div style={{fontSize:11,color:C.muted,marginBottom:8}}>{filtered.length} জন</div>
      {loading&&!usersRaw?[...Array(4)].map((_,i)=><div key={i} className="sk"/>):
       filtered.length===0?<div className="empty"><div className="ei">👤</div><p>কেউ নেই</p></div>:
       filtered.map((u,i)=>{
        const nm=u.Name||u.name||"অজানা",ph=u.Phone||u.phone||"—";
        const st=(u.Status||"inactive").toLowerCase();
        const fkey=u._fbKey||phoneKey(ph);
        const c=parseInt(u.totalCorrect)||0,w=parseInt(u.totalWrong)||0,tot=c+w;
        const acc=tot?Math.round(c/tot*100):0;
        const mins=parseInt(u.totalMinutes||u.studyMinutes||u.totalTime||0);
        return(
          <div key={fkey||i} className="card" style={{padding:11}}>
            <div style={{display:"flex",alignItems:"center",gap:9,marginBottom:8}} onClick={()=>setDetail(u)} style={{cursor:"pointer",display:"flex",alignItems:"center",gap:9,marginBottom:8}}>
              <div className="av">{initials(nm)}</div>
              <div style={{flex:1,minWidth:0}}>
                <div style={{fontWeight:700,fontSize:13}}>{nm}</div>
                <div style={{fontSize:10,color:C.muted}}>📱 {ph}</div>
              </div>
              <div style={{textAlign:"right"}}>
                <span className={`pill ${st==="active"?"pa":"pi"}`}>{st==="active"?"✅":"🔴"} {st==="active"?"অ্যাক্টিভ":"ইনঅ্যাক্টিভ"}</span>
                {tot>0&&<div style={{fontSize:9,color:acc>=70?C.green:acc>=40?C.yellow:C.red,marginTop:2,fontWeight:700}}>{acc}%</div>}
              </div>
            </div>
            <div style={{display:"flex",gap:6,marginBottom:8}}>
              {[[C.green,c,"✅"],[C.red,w,"❌"],[C.accent,mins,"⏱"]].map(([cl,val,ic])=>(
                <div key={ic} style={{textAlign:"center",flex:1,background:C.panel,borderRadius:7,padding:"5px 2px"}}>
                  <div style={{color:cl,fontWeight:700,fontSize:13}}>{val}</div>
                  <div style={{color:C.muted,fontSize:9}}>{ic}</div>
                </div>
              ))}
            </div>
            <div style={{display:"flex",gap:6}}>
              {st!=="active"&&<button className="btn bs" style={{flex:1,justifyContent:"center",fontSize:11}} disabled={!!busy} onClick={()=>activate(u)}>{busy===fkey?"⏳":"✅ অ্যাক্টিভ"}</button>}
              <button className="btn bg" style={{flex:1,justifyContent:"center",fontSize:11}} onClick={()=>setNotify(u)}>📣</button>
              <button className="btn bp" style={{flex:1,justifyContent:"center",fontSize:11}} onClick={()=>setDetail(u)}>👁</button>
            </div>
          </div>
        );
       })
      }
      {notify&&<NotifyModal user={notify} onClose={()=>setNotify(null)} push={push}/>}
    </div>
  );
}

function StudentDetail({user,onBack,push}){
  const nm=user.Name||user.name||"অজানা";
  const ph=(user.Phone||user.phone||"").replace(/^'+/,"");
  const st=(user.Status||user.status||"inactive").toLowerCase();
  const phK=phoneKey(ph);
  const{data:timeData}=useFB(`Analytics/Time/${phK}`);
  const{data:subjData}=useFB(`Analytics/Subject/${phK}`);

  const c=parseInt(user.totalCorrect)||0;
  const w=parseInt(user.totalWrong)||0;
  const tot=c+w,acc=tot?Math.round(c/tot*100):0;
  const mins=parseInt(user.totalMinutes||user.studyMinutes||user.totalTime||0);
  const dailyTime=useMemo(()=>timeData&&typeof timeData==="object"
    ?Object.entries(timeData).sort(([a],[b])=>a.localeCompare(b)).slice(-7).map(([d,v])=>({l:d.slice(5),v:parseInt(v)||0})):[]
  ,[timeData]);
  const subjEntries=useMemo(()=>subjData&&typeof subjData==="object"?Object.entries(subjData):[],[subjData]);

  return(
    <div className="fs">
      <div className="fsh">
        <button className="bk" onClick={onBack}>←</button>
        <div className="av">{initials(nm)}</div>
        <div style={{flex:1,minWidth:0}}>
          <div style={{fontWeight:700,fontSize:14,overflow:"hidden",textOverflow:"ellipsis",whiteSpace:"nowrap"}}>{nm}</div>
          <div style={{fontSize:10,color:C.muted}}>📱 {ph}</div>
        </div>
        <span className={`pill ${st==="active"?"pa":"pi"}`}>{st==="active"?"✅":"🔴"}</span>
      </div>
      <div style={{padding:"12px 12px 70px"}}>
        <div className="card">
          <div style={{display:"flex",alignItems:"center",gap:12}}>
            <Ring val={c} max={tot} color={acc>=70?C.green:acc>=40?C.yellow:C.red}/>
            <div style={{flex:1}}>
              <div style={{fontSize:18,fontWeight:700,color:acc>=70?C.green:acc>=40?C.yellow:C.red,marginBottom:3}}>{acc}% Accuracy</div>
              <div style={{fontSize:11,color:C.muted}}>✅ {c} &nbsp; ❌ {w} &nbsp; 🎯 {tot}</div>
            </div>
          </div>
        </div>
        <div className="sg">
          <div className="sc tp" data-icon="⏱"><div className="sl">মোট সময়</div><div className="sv sv-p" style={{fontSize:18}}>{mins<60?mins+"মি":~~(mins/60)+"ঘণ্টা"}</div></div>
          <div className="sc tb" data-icon="📅"><div className="sl">শেষ সক্রিয়</div><div style={{fontSize:12,fontWeight:700,marginTop:5,color:C.accent}}>{timeAgo(user.lastActive||user.Timestamp)}</div></div>
        </div>
        {dailyTime.length>0&&<div className="card"><div className="ct">⏱ দৈনিক সময়</div><Bar data={dailyTime} color={C.purple}/></div>}
        {subjEntries.length>0&&(
          <div className="card">
            <div className="ct">📚 বিষয়ভিত্তিক</div>
            {subjEntries.map(([sub,sv])=>{
              const sc=sv.correct||0,sw2=sv.wrong||0,st2=sc+sw2,sa=st2?Math.round(sc/st2*100):0;
              return(
                <div key={sub} className="srow">
                  <div style={{flex:1}}>
                    <div style={{fontWeight:600,fontSize:12}}>{sub}</div>
                    <div style={{display:"flex",alignItems:"center",marginTop:2}}><div className="sbar"><div className="sbar-f" style={{width:sa+"%",background:sa>=70?C.green:sa>=40?C.yellow:C.red}}/></div></div>
                    <div style={{fontSize:9,color:C.muted}}>✅{sc} ❌{sw2}</div>
                  </div>
                  <div style={{fontWeight:700,fontSize:14,color:sa>=70?C.green:sa>=40?C.yellow:C.red,minWidth:32,textAlign:"right"}}>{sa}%</div>
                </div>
              );
            })}
          </div>
        )}
        <NotifyModal user={user} onClose={onBack} push={push} inline/>
      </div>
    </div>
  );
}

/* ══════════ REPORTS — hard delete ══════════ */
function ReportsPage({push,tick}){
  const{data:rRaw,loading}=useFB("Reports",tick);
  const[done,setDone]=useState(new Set());
  const[editing,setEditing]=useState(null);
  const reports=useMemo(()=>toArr(rRaw).filter(r=>!done.has(r._fbKey||r.row)).slice(-30).reverse(),[rRaw,done]);
  return(
    <div className="page">
      <div style={{display:"flex",alignItems:"center",justifyContent:"space-between",marginBottom:11}}>
        <div style={{fontSize:11,color:C.muted}}>{reports.length}টি রিপোর্ট</div>
        {loading&&<span style={{fontSize:10,color:C.muted}}>⏳</span>}
      </div>
      {loading&&!rRaw?[...Array(3)].map((_,i)=><div key={i} className="sk"/>):
       reports.length===0?<div className="empty"><div className="ei">📋</div><p>রিপোর্ট নেই! 🎉</p></div>:
       reports.map((r,i)=>{
        const isMCQ=(r.QType||r.qtype||"MCQ").toLowerCase()!=="written";
        const qid2=r.QuestionID||r.questionId;
        return(
         <div key={r._fbKey||i} className="rc" style={{borderLeft:`3px solid ${isMCQ?C.accent:C.purple}`}}>
           <div style={{display:"flex",alignItems:"center",gap:7,marginBottom:6}}>
             <span style={{background:isMCQ?`${C.accent}22`:`${C.purple}22`,color:isMCQ?C.accent:C.purple,border:`1px solid ${isMCQ?C.accent:C.purple}44`,borderRadius:6,padding:"2px 8px",fontSize:10,fontWeight:700}}>{isMCQ?"❓ MCQ":"✍️ Written"}</span>
             <div style={{fontWeight:700,fontSize:13,flex:1}}>{r.Subject||r.subject||"অজানা"}</div>
           </div>
           <div className="rm">
             <span>📱 {(r.Phone||r.phone||"—").toString().replace(/^'+/,"")}</span>
             {(r.SubTopic||r.subtopic)&&<span>📌 {r.SubTopic||r.subtopic}</span>}
             {qid2&&<span style={{color:C.accent}}>#{qid2}</span>}
           </div>
           <div className="ri">{r.Issue||r.issue||r.Question||r.question||"বিস্তারিত নেই"}</div>
           <button className="btn bp bb" style={{marginTop:8,background:isMCQ?C.accent:C.purple}} onClick={()=>setEditing(r)}>✏️ এডিট ও সমাধান</button>
         </div>
        );
       })
      }
      {editing&&<ReportEditModal report={editing} onClose={()=>setEditing(null)} onDone={key=>{setDone(p=>new Set([...p,key]));setEditing(null);invalidate("Reports");}} push={push}/>}
    </div>
  );
}

function ReportEditModal({report,onClose,onDone,push}){
  useModalBack(onClose);
  const[step,setStep]=useState(1);
  const[qdata,setQdata]=useState(null);
  const[loadQ,setLoadQ]=useState(true);
  const[saving,setSaving]=useState(false);
  const[notifying,setNotifying]=useState(false);
  const[question,setQuestion]=useState("");
  const[opt1,setOpt1]=useState(""); const[opt2,setOpt2]=useState("");
  const[opt3,setOpt3]=useState(""); const[opt4,setOpt4]=useState("");
  const[correct,setCorrect]=useState("");
  const[explanation,setExplanation]=useState("");
  const[technique,setTechnique]=useState("");
  const[qtype,setQtype]=useState("mcq");
  const qid=(report.QuestionID||report.questionId||"").toString();
  const qsheet=(report.QSheet||report.qsheet||"").toString().trim();

  useEffect(()=>{
    if(!qid){setLoadQ(false);return;}
    let cancelled=false;
    (async()=>{
      setLoadQ(true);
      const sheetsToTry=qsheet?[qsheet]:["QBank","Quiz","Study"];
      for(const t of sheetsToTry){
        try{
          const raw=await loadPath(t);
          const arr=toArr(raw);
          const qNorm=qid.replace(/^0+/,"");
          const q=arr.find(x=>{
            const xid=(x.ID||x.id||x.SL||x.sl||"").toString().replace(/^0+/,"");
            return xid===qNorm;
          });
          if(q&&!cancelled){
            setQdata({...q,_tab:t});
            setQuestion(q.Question||q.question||"");
            setOpt1(q.Opt1||q.opt1||q.Option1||q.option1||"");
            setOpt2(q.Opt2||q.opt2||q.Option2||q.option2||"");
            setOpt3(q.Opt3||q.opt3||q.Option3||q.option3||"");
            setOpt4(q.Opt4||q.opt4||q.Option4||q.option4||"");
            setCorrect(q.Correct||q.correct||"");
            setExplanation(q.Explanation||q.explanation||"");
            setTechnique(q.Technique||q.technique||"");
            const qt=(q.QType||q.qtype||"MCQ").toLowerCase();
            setQtype(t==="Study"?"study":qt==="written"?"written":"mcq");
            break;
          }
        }catch(_){}
      }
      if(!cancelled)setLoadQ(false);
    })();
    return()=>{cancelled=true;};
  },[qid,qsheet]);

  const save=async()=>{
    setSaving(true);
    try{
      if(qdata&&qid){
        const t=qdata._tab||"QBank";
        const fkey=qdata._fbKey;
        let patch={};
        if(qtype==="mcq"){
          const o1k=qdata.Opt1!=null?"Opt1":qdata.opt1!=null?"opt1":"Option1";
          const o2k=o1k.replace(/1$/,"2");const o3k=o1k.replace(/1$/,"3");const o4k=o1k.replace(/1$/,"4");
          patch={Question:question,[o1k]:opt1,[o2k]:opt2,[o3k]:opt3,[o4k]:opt4,Correct:correct,Explanation:explanation,Technique:technique};
        } else {
          patch={Question:question,Explanation:explanation,Technique:technique};
          if(qtype==="study")patch.Correct=correct;
        }
        if(fkey)await fbPatch(`${t}/${fkey}`,patch);
        invalidate(t);
        gasBg({action:"updateField",sheet:t,id:qid,field:"question",content:encodeURIComponent(question)});
        if(explanation)gasBg({action:"updateField",sheet:t,id:qid,field:"explanation",content:encodeURIComponent(explanation)});
      }
      push("success","✅ সেভ হয়েছে!","");
      setStep(2);
    }catch(e){push("error","Save ব্যর্থ",String(e?.message||e||""));}
    setSaving(false);
  };

  const doNotifyAndDelete=async()=>{
    setNotifying(true);
    try{
      const phone=(report.Phone||report.phone||"").toString().replace(/^'+/,"").trim();
      const subject=(report.Subject||report.subject||"প্রশ্নটি").toString();
      const phK=phoneKey(phone);
      const notifTitle="✅ রিপোর্ট সমাধান হয়েছে!";
      const notifBody=`"${subject}" সংশোধন হয়েছে।`;

      await fbSet(`Notifications/${phK}/notif_${Date.now()}`,{type:"report_resolved",title:notifTitle,body:notifBody,time:nowTs(),read:false});
      try{
        await Promise.race([
          fetch(GAS+"?"+new URLSearchParams({action:"personalNotify",phone,title:encodeURIComponent(notifTitle),body:encodeURIComponent(notifBody)})),
          new Promise((_,rej)=>setTimeout(()=>rej(new Error("t")),7000))
        ]);
      }catch(_){}

      // Hard delete: Firebase
      const reportKey=report._fbKey||report.row;
      if(reportKey){
        await fbDelete(`Reports/${reportKey}`);
        invalidate("Reports");
      }
      // Hard delete: Google Sheet — GAS expects 'key' not 'reportKey'
      if(reportKey){
        gasBg({action:"deleteReport",key:reportKey,phone,questionId:qid});
      }

      push("success","✅ নোটিফাই ও ডিলিট!","Report মুছে গেছে");
      onDone(reportKey);
    }catch(e){push("error","ব্যর্থ",String(e?.message||e||""));}
    setNotifying(false);
  };

  const ac=qtype==="mcq"?C.accent:qtype==="study"?C.green:C.purple;

  return(
    <div className="ovl">
      <div className="modal">
        <div className="mh"/>
        <div className="steps">
          <div className={`step${step===1?" act":step>1?" done":""}`}>① এডিট</div>
          <div className={`step${step===2?" act":""}`}>② নোটিফাই ও ডিলিট</div>
        </div>
        {step===1&&<>
          <div style={{background:"#ef444412",border:"1px solid #ef444430",borderRadius:9,padding:"7px 10px",marginBottom:10}}>
            <div style={{fontSize:10,color:C.red,fontWeight:700,marginBottom:2}}>🚨 {(report.Phone||report.phone||"").toString().replace(/^'+/,"")} · {report.Subject||report.subject}</div>
            <div style={{fontSize:11,color:C.text}}>{report.Issue||report.issue||"—"}</div>
          </div>
          {loadQ&&<><div className="sk" style={{height:52,marginBottom:8}}/><div className="sk" style={{height:36}}/></>}
          {!loadQ&&!qdata&&<div style={{textAlign:"center",color:C.muted,padding:"18px 0",fontSize:12}}>প্রশ্ন #{qid||"—"} পাওয়া যায়নি।</div>}
          {!loadQ&&qdata&&qtype==="mcq"&&<>
            <div className="fld"><label>❓ প্রশ্ন</label><textarea className="ta" value={question} onChange={e=>setQuestion(e.target.value)} style={{minHeight:60}}/></div>
            <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:7,marginBottom:10}}>
              {[[opt1,setOpt1,"A"],[opt2,setOpt2,"B"],[opt3,setOpt3,"C"],[opt4,setOpt4,"D"]].map(([v,sv,lbl])=>(
                <div key={lbl} className="fld" style={{margin:0}}><label>{lbl}</label><input className="inp" value={v} onChange={e=>sv(e.target.value)}/></div>
              ))}
            </div>
            <div className="fld">
              <label>✅ সঠিক উত্তর</label>
              <div style={{display:"flex",gap:4,flexWrap:"wrap",marginBottom:5}}>
                {[opt1,opt2,opt3,opt4].filter(Boolean).map((o,i)=>(
                  <button key={i} type="button" className={`cc${correct===o?" on":""}`} onClick={()=>setCorrect(o)}>{o.slice(0,16)}</button>
                ))}
              </div>
              <input className="inp" value={correct} onChange={e=>setCorrect(e.target.value)}/>
            </div>
            <div className="fld"><label>📖 Explanation</label><textarea className="ta" value={explanation} onChange={e=>setExplanation(e.target.value)} style={{minHeight:60}}/></div>
            <div className="fld"><label>💡 Technique</label><textarea className="ta" value={technique} onChange={e=>setTechnique(e.target.value)} style={{minHeight:45}}/></div>
          </>}
          {!loadQ&&qdata&&qtype!=="mcq"&&<>
            <div className="fld"><label>প্রশ্ন</label><textarea className="ta" value={question} onChange={e=>setQuestion(e.target.value)} style={{minHeight:75}}/></div>
            {qtype==="study"&&<div className="fld"><label>✅ উত্তর</label><textarea className="ta" value={correct} onChange={e=>setCorrect(e.target.value)} style={{minHeight:60}}/></div>}
            <div className="fld"><label>📖 Explanation</label><textarea className="ta" value={explanation} onChange={e=>setExplanation(e.target.value)} style={{minHeight:60}}/></div>
            <div className="fld"><label>💡 Technique</label><textarea className="ta" value={technique} onChange={e=>setTechnique(e.target.value)} style={{minHeight:45}}/></div>
          </>}
          <div style={{display:"flex",gap:6,marginTop:4}}>
            <button type="button" className="btn bg" style={{flex:1,justifyContent:"center"}} onClick={onClose}>বাতিল</button>
            <button type="button" className="btn bp" style={{flex:2,justifyContent:"center",background:ac}} disabled={saving||loadQ||!qdata} onClick={save}>{saving?"⏳ সেভ হচ্ছে...":"💾 সেভ করুন →"}</button>
          </div>
        </>}
        {step===2&&<>
          <div className="mt">📣 নোটিফাই ও রিপোর্ট মুছুন</div>
          <div style={{background:"#22c55e12",border:"1px solid #22c55e30",borderRadius:10,padding:"11px",marginBottom:12}}>
            <div style={{fontSize:13,fontWeight:700,color:C.green,marginBottom:4}}>✅ প্রশ্ন আপডেট সম্পন্ন!</div>
          </div>
          <div style={{background:"#ef444412",border:"1px solid #ef444330",borderRadius:9,padding:"8px 11px",marginBottom:14,fontSize:11,color:C.red}}>
            ⚠️ Firebase ও Google Sheet থেকে স্থায়ীভাবে মুছে যাবে।
          </div>
          <div style={{display:"flex",gap:6}}>
            <button className="btn bg" style={{flex:1,justifyContent:"center"}} onClick={()=>onDone(report._fbKey||report.row)}>এড়িয়ে যান</button>
            <button className="btn" style={{flex:2,justifyContent:"center",background:C.green,color:"#fff"}} disabled={notifying} onClick={doNotifyAndDelete}>
              {notifying?"⏳...":"✅ নোটিফাই ও ডিলিট"}
            </button>
          </div>
        </>}
      </div>
    </div>
  );
}

/* ══════════ ENTRY ══════════ */
function EntryPage({push}){
  const[mode,setMode]=useState("QBank");
  const[qtype,setQtype]=useState("MCQ");
  const[saving,setSaving]=useState(false);
  const[uploading,setUp]=useState(false);
  const[question,setQuestion]=useState("");
  const[opt1,setOpt1]=useState("");const[opt2,setOpt2]=useState("");
  const[opt3,setOpt3]=useState("");const[opt4,setOpt4]=useState("");
  const[correct,setCorrect]=useState("");
  const[subject,setSubject]=useState("");
  const[topic,setTopic]=useState("");
  const[subtopic,setSubtopic]=useState("");
  const[explanation,setExplanation]=useState("");
  const[technique,setTechnique]=useState("");
  const[imgUrl,setImgUrl]=useState("");
  const[subjects,setSubjects]=useState([]);

  useEffect(()=>{
    loadPath(mode).then(raw=>{
      const arr=toArr(raw);
      setSubjects([...new Set(arr.map(q=>q.Subject||q.subject||"").filter(Boolean))]);
    }).catch(()=>{});
  },[mode]);

  const reset=()=>{setQuestion("");setOpt1("");setOpt2("");setOpt3("");setOpt4("");setCorrect("");setExplanation("");setTechnique("");setImgUrl("");};
  const handleImg=async e=>{
    const f=e.target.files[0];if(!f)return;
    setUp(true);
    try{const u=await uploadImg(f);setImgUrl(u);push("success","ছবি আপলোড হয়েছে","");}
    catch{push("error","আপলোড ব্যর্থ","");}
    setUp(false);
  };
  const submit=async()=>{
    if(!question.trim()&&mode!=="Study"){push("warn","প্রশ্ন লিখুন","");return;}
    if(!subject.trim()){push("warn","বিষয় লিখুন","");return;}
    setSaving(true);
    try{
      const ts=nowTs(),id=Date.now();
      let rec={};
      if(mode==="Quiz")rec={ID:id,Question:question,Opt1:opt1,Opt2:opt2,Opt3:opt3,Opt4:opt4,Correct:correct,Subject:subject,Sub_topic:subtopic,Explanation:explanation,Technique:technique,QType:qtype,Timestamp:ts,Image:imgUrl};
      else if(mode==="QBank")rec={ID:id,Question:question,Opt1:opt1,Opt2:opt2,Opt3:opt3,Opt4:opt4,Correct:correct,Subject:subject,Topic:topic,Sub_topic:subtopic,Explanation:explanation,Technique:technique,QType:qtype,Timestamp:ts,Image:imgUrl};
      else rec={ID:id,Subject:subject,Sub_topic:subtopic,Explanation:explanation,Technique:technique,Timestamp:ts,Image:imgUrl};
      await fbPush(mode,rec);
      invalidate(mode);
      gasPost({targetTab:mode,question,opt1,opt2,opt3,opt4,correct,subject,topic,sub_topic:subtopic,explanation,technique,qType:qtype,timestamp:ts});
      push("success","✅ সেভ হয়েছে!",`${mode} #${id}`);
      reset();
    }catch(e){push("error","ব্যর্থ",e.message);}
    setSaving(false);
  };
  return(
    <div className="page">
      <div className="ftabs">{["QBank","Quiz","Study"].map(m=><button key={m} className={`ftab${mode===m?" on":""}`} onClick={()=>setMode(m)}>{m}</button>)}</div>
      {mode!=="Study"&&<div style={{display:"flex",gap:7,marginBottom:11}}>{["MCQ","Written"].map(t=><button key={t} className={`tp2${qtype===t?" on":""}`} onClick={()=>setQtype(t)}>{t}</button>)}</div>}
      {mode!=="Study"&&<div className="fld"><label>❓ প্রশ্ন</label><textarea className="ta" value={question} onChange={e=>setQuestion(e.target.value)} style={{minHeight:80}}/></div>}
      {mode!=="Study"&&qtype==="MCQ"&&<>
        <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:8}}>
          <div className="fld"><label>A</label><input className="inp" value={opt1} onChange={e=>setOpt1(e.target.value)}/></div>
          <div className="fld"><label>B</label><input className="inp" value={opt2} onChange={e=>setOpt2(e.target.value)}/></div>
          <div className="fld"><label>C</label><input className="inp" value={opt3} onChange={e=>setOpt3(e.target.value)}/></div>
          <div className="fld"><label>D</label><input className="inp" value={opt4} onChange={e=>setOpt4(e.target.value)}/></div>
        </div>
        <div className="fld">
          <label>✅ সঠিক উত্তর</label>
          <div style={{display:"flex",gap:5,flexWrap:"wrap",marginBottom:6}}>
            {[opt1,opt2,opt3,opt4].filter(Boolean).map((o,i)=><button key={i} type="button" className={`cc${correct===o?" on":""}`} onClick={()=>setCorrect(o)}>{o.slice(0,16)}</button>)}
          </div>
          <input className="inp" value={correct} onChange={e=>setCorrect(e.target.value)} placeholder="বা সরাসরি লিখুন..."/>
        </div>
      </>}
      <div className="fld">
        <label>📚 বিষয়</label>
        <input className="inp" list="sl" value={subject} onChange={e=>setSubject(e.target.value)} placeholder="Subject..."/>
        <datalist id="sl">{subjects.map((s,i)=><option key={i} value={s}/>)}</datalist>
      </div>
      {mode==="QBank"&&<div className="fld"><label>📂 Topic</label><input className="inp" value={topic} onChange={e=>setTopic(e.target.value)}/></div>}
      <div className="fld"><label>📌 Sub Topic</label><input className="inp" value={subtopic} onChange={e=>setSubtopic(e.target.value)}/></div>
      <div className="fld"><label>📖 Explanation</label><textarea className="ta" value={explanation} onChange={e=>setExplanation(e.target.value)} style={{minHeight:80}}/></div>
      <div className="fld"><label>💡 Technique</label><textarea className="ta" value={technique} onChange={e=>setTechnique(e.target.value)} style={{minHeight:60}}/></div>
      <div className="fld">
        <label>🖼 ছবি</label>
        <label style={{display:"flex",alignItems:"center",gap:7,background:C.panel,border:`1px solid ${C.border}`,borderRadius:9,padding:"9px 12px",cursor:"pointer",fontSize:12,color:C.muted}}>
          {uploading?"⏳ আপলোড হচ্ছে...":"📷 ছবি বেছে নিন"}
          <input type="file" accept="image/*" style={{display:"none"}} onChange={handleImg}/>
          {imgUrl&&<a href={imgUrl} target="_blank" rel="noreferrer" style={{fontSize:11,color:C.accent,marginLeft:"auto"}} onClick={e=>e.stopPropagation()}>দেখুন ↗</a>}
        </label>
        {imgUrl&&<img src={imgUrl} style={{width:"100%",borderRadius:9,marginTop:6,border:`1px solid ${C.border}`}} alt="p"/>}
      </div>
      <button className="btn bp bb" style={{marginTop:4}} disabled={saving} onClick={submit}>{saving?"⏳ সেভ হচ্ছে...":"💾 সেভ করুন"}</button>
    </div>
  );
}

/* ══════════ CONTENT MANAGER ══════════ */
function ContentManagerPage({push,tick}){
  const[tab,setTab]=useState("browse");
  return(
    <div className="page" style={{paddingTop:0}}>
      <div style={{position:"sticky",top:0,zIndex:40,background:C.bg,paddingTop:13,paddingBottom:8}}>
        <div className="atabs">
          <button className={`atab${tab==="browse"?" on":""}`} onClick={()=>setTab("browse")}>📋 Browse</button>
          <button className={`atab${tab==="rename"?" on":""}`} onClick={()=>setTab("rename")}>✏️ Rename</button>
          <button className={`atab${tab==="audience"?" on":""}`} onClick={()=>setTab("audience")}>🎯 Audience</button>
          <button className={`atab${tab==="delete"?" on":""}`} onClick={()=>setTab("delete")}>🗑️ Delete</button>
        </div>
      </div>
      {tab==="browse"&&<BrowseTab push={push} tick={tick}/>}
      {tab==="rename"&&<RenameTab push={push} tick={tick}/>}
      {tab==="audience"&&<AudienceTagRenameTab push={push} tick={tick}/>}
      {tab==="delete"&&<DeleteTab push={push} tick={tick}/>}
    </div>
  );
}

function BrowseTab({push,tick}){
  const[sheet,setSheet]=useState("QBank");
  const{data:raw,loading}=useFB(sheet,tick);
  const[search,setSearch]=useState("");
  const[filterSub,setFilterSub]=useState("all");
  const[editing,setEditing]=useState(null);
  const[delTarget,setDelTarget]=useState(null);
  const[delLoading,setDelLoading]=useState(false);
  const[page,setPage]=useState(0);
  const PAGE=20;

  const allQ=useMemo(()=>toArr(raw).reverse(),[raw]);
  const subjects=useMemo(()=>["all",...new Set(allQ.map(q=>(q.Subject||q.subject||"").trim()).filter(Boolean))]
  ,[allQ]);

  const filtered=useMemo(()=>{
    let arr=allQ;
    if(filterSub!=="all")arr=arr.filter(q=>(q.Subject||q.subject||"").trim()===filterSub);
    if(search.trim()){
      const qlo=search.toLowerCase();
      arr=arr.filter(q=>[(q.Question||q.question||""),(q.Subject||q.subject||""),(q.Sub_topic||q.sub_topic||""),(q.Correct||q.correct||"")].join(" ").toLowerCase().includes(qlo));
    }
    return arr;
  },[allQ,filterSub,search]);

  useEffect(()=>setPage(0),[sheet,filterSub,search]);

  const pageSlice=useMemo(()=>filtered.slice(page*PAGE,(page+1)*PAGE),[filtered,page]);
  const totalPages=Math.ceil(filtered.length/PAGE);

  const hardDelete=async()=>{
    if(!delTarget)return;
    setDelLoading(true);
    try{
      const fkey=delTarget._fbKey;
      const qid=(delTarget.ID||delTarget.id||"").toString();
      if(fkey){await fbDelete(`${sheet}/${fkey}`);invalidate(sheet);}
      // gasBg deleteByIds skipped — Firebase already deleted above
      push("success","🗑️ ডিলিট!",`#${qid}`);
      setDelTarget(null);
    }catch(e){push("error","ডিলিট ব্যর্থ",e.message);}
    setDelLoading(false);
  };

  return(
    <>
      <div style={{display:"flex",gap:6,marginBottom:8}}>
        {["QBank","Quiz","Study"].map(s=>(
          <button key={s} className={`ftab${sheet===s?" on":""}`} onClick={()=>{setSheet(s);setFilterSub("all");setSearch("");}}>{s}</button>
        ))}
      </div>
      <div className="sw" style={{marginBottom:8}}>
        <span className="si">🔍</span>
        <input className="inp" placeholder="প্রশ্ন, বিষয়..." value={search} onChange={e=>setSearch(e.target.value)}/>
      </div>
      {subjects.length>2&&(
        <div className="ftabs" style={{marginBottom:8}}>
          {subjects.slice(0,8).map(s=>(
            <button key={s} className={`ftab${filterSub===s?" on":""}`} onClick={()=>setFilterSub(s)}>{s==="all"?"সব":s}</button>
          ))}
        </div>
      )}
      <div style={{fontSize:11,color:C.muted,marginBottom:8,display:"flex",justifyContent:"space-between"}}>
        <span>{loading?"⏳":`${filtered.length} / ${allQ.length}টি`}</span>
        {totalPages>1&&<span style={{color:C.accent}}>{page+1}/{totalPages}</span>}
      </div>
      {loading&&!raw?[...Array(4)].map((_,i)=><div key={i} className="sk"/>):
       filtered.length===0?<div className="empty"><div className="ei">📋</div><p>কিছু নেই</p></div>:
       pageSlice.map((q,i)=>{
        const qid=(q.ID||q.id||"").toString();
        const qtext=(q.Question||q.question||"(নোট)").slice(0,80);
        const sub=(q.Subject||q.subject||"—");
        const tp=(q.Sub_topic||q.sub_topic||"");
        const qt=(q.QType||q.qtype||"MCQ").toLowerCase();
        return(
          <div key={q._fbKey||i} className="qcard">
            <div style={{display:"flex",gap:6,marginBottom:5,alignItems:"flex-start"}}>
              <span className={`qtag ${qt==="written"?"qtag-wr":"qtag-mcq"}`}>{qt==="written"?"✍️":"❓"}</span>
              <span style={{fontSize:9,color:C.muted,marginTop:1}}>#{qid}</span>
              <div style={{flex:1}}/>
              <button className="btn" style={{padding:"3px 9px",fontSize:10,background:C.accent+"22",color:C.accent,border:`1px solid ${C.accent}33`}} onClick={()=>setEditing(q)}>✏️</button>
              <button className="btn" style={{padding:"3px 9px",fontSize:10,background:C.red+"22",color:C.red,border:`1px solid ${C.red}33`}} onClick={()=>setDelTarget(q)}>🗑️</button>
            </div>
            <div className="qcard-q">{qtext}{qtext.length>=80?"…":""}</div>
            <div className="qcard-meta">
              <span className="qtag qtag-sub">📚 {sub}</span>
              {tp&&<span className="qtag qtag-tp">📌 {tp.slice(0,25)}</span>}
            </div>
          </div>
        );
       })
      }
      {totalPages>1&&(
        <div style={{display:"flex",gap:6,justifyContent:"center",marginTop:8}}>
          <button className="btn bg" disabled={page===0} onClick={()=>setPage(p=>p-1)}>← আগে</button>
          <span style={{padding:"7px 12px",fontSize:11,color:C.muted}}>{page+1} / {totalPages}</span>
          <button className="btn bg" disabled={page>=totalPages-1} onClick={()=>setPage(p=>p+1)}>পরে →</button>
        </div>
      )}
      {editing&&<InlineEditModal q={editing} sheet={sheet} onClose={()=>setEditing(null)} onSaved={()=>{setEditing(null);invalidate(sheet);}} push={push}/>}
      {delTarget&&<DeleteWarningModal
        title="এই প্রশ্নটি ডিলিট করবেন?"
        description={`"${(delTarget.Question||delTarget.question||"নোট").slice(0,60)}…" Firebase ও Google Sheet থেকে মুছে যাবে।`}
        onConfirm={hardDelete} onCancel={()=>setDelTarget(null)} loading={delLoading}
      />}
    </>
  );
}

function InlineEditModal({q,sheet,onClose,onSaved,push}){
  useModalBack(onClose);
  const[saving,setSaving]=useState(false);
  const qt=(q.QType||q.qtype||"MCQ").toLowerCase();
  const[question,setQuestion]=useState(q.Question||q.question||"");
  const[opt1,setOpt1]=useState(q.Opt1||q.opt1||q.Option1||"");
  const[opt2,setOpt2]=useState(q.Opt2||q.opt2||q.Option2||"");
  const[opt3,setOpt3]=useState(q.Opt3||q.opt3||q.Option3||"");
  const[opt4,setOpt4]=useState(q.Opt4||q.opt4||q.Option4||"");
  const[correct,setCorrect]=useState(q.Correct||q.correct||"");
  const[explanation,setExplanation]=useState(q.Explanation||q.explanation||"");
  const[technique,setTechnique]=useState(q.Technique||q.technique||"");
  const qid=(q.ID||q.id||"").toString();
  const ac=qt==="written"?C.purple:sheet==="Study"?C.green:C.accent;

  const save=async()=>{
    setSaving(true);
    try{
      const fkey=q._fbKey;
      let patch={};
      if(sheet==="Study"){
        patch={Question:question,Correct:correct,Explanation:explanation,Technique:technique};
      } else if(qt==="written"){
        patch={Question:question,Explanation:explanation,Technique:technique};
      } else {
        const o1k=q.Opt1!=null?"Opt1":q.opt1!=null?"opt1":"Option1";
        const o2k=o1k.replace(/1$/,"2"),o3k=o1k.replace(/1$/,"3"),o4k=o1k.replace(/1$/,"4");
        patch={Question:question,[o1k]:opt1,[o2k]:opt2,[o3k]:opt3,[o4k]:opt4,Correct:correct,Explanation:explanation,Technique:technique};
      }
      if(fkey)await fbPatch(`${sheet}/${fkey}`,patch);
      // Sync all changed fields to Google Sheet
      const syncFields=[
        ["question",question],
        ["explanation",explanation],
        ["technique",technique],
        ["correct",correct],
        ["opt1",opt1],["opt2",opt2],["opt3",opt3],["opt4",opt4],
      ];
      syncFields.forEach(([f,v])=>{
        if(v&&v.trim()) gasBg({action:"updateField",sheet,id:qid,field:f,content:encodeURIComponent(v)});
      });
      push("success","✅ আপডেট!",`#${qid}`);
      onSaved();
    }catch(e){push("error","ব্যর্থ",e.message);}
    setSaving(false);
  };

  return(
    <div className="ovl">
      <div className="modal">
        <div className="mh"/>
        <div style={{display:"flex",alignItems:"center",gap:8,marginBottom:12}}>
          <span style={{background:`${ac}22`,color:ac,border:`1px solid ${ac}44`,borderRadius:6,padding:"3px 9px",fontSize:11,fontWeight:700}}>
            {sheet==="Study"?"📖 Study":qt==="written"?"✍️ Written":"❓ MCQ"}
          </span>
          <span style={{fontSize:10,color:C.muted}}>#{qid} · {sheet}</span>
        </div>
        <div className="fld"><label>প্রশ্ন</label><textarea className="ta" value={question} onChange={e=>setQuestion(e.target.value)} style={{minHeight:70}}/></div>
        {sheet!=="Study"&&qt!=="written"&&<>
          <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:7,marginBottom:10}}>
            {[[opt1,setOpt1,"A"],[opt2,setOpt2,"B"],[opt3,setOpt3,"C"],[opt4,setOpt4,"D"]].map(([v,sv,lbl])=>(
              <div key={lbl} className="fld" style={{margin:0}}><label>{lbl}</label><input className="inp" value={v} onChange={e=>sv(e.target.value)}/></div>
            ))}
          </div>
          <div className="fld">
            <label>✅ সঠিক উত্তর</label>
            <div style={{display:"flex",gap:4,flexWrap:"wrap",marginBottom:5}}>
              {[opt1,opt2,opt3,opt4].filter(Boolean).map((o,i)=>(
                <button key={i} type="button" className={`cc${correct===o?" on":""}`} onClick={()=>setCorrect(o)}>{o.slice(0,14)}</button>
              ))}
            </div>
            <input className="inp" value={correct} onChange={e=>setCorrect(e.target.value)}/>
          </div>
        </>}
        {sheet==="Study"&&<div className="fld"><label>✅ উত্তর</label><textarea className="ta" value={correct} onChange={e=>setCorrect(e.target.value)} style={{minHeight:60}}/></div>}
        <div className="fld"><label>📖 Explanation</label><textarea className="ta" value={explanation} onChange={e=>setExplanation(e.target.value)} style={{minHeight:60}}/></div>
        <div className="fld"><label>💡 Technique</label><textarea className="ta" value={technique} onChange={e=>setTechnique(e.target.value)} style={{minHeight:45}}/></div>
        <div style={{display:"flex",gap:7,marginTop:4}}>
          <button className="btn bg" style={{flex:1,justifyContent:"center"}} onClick={onClose}>বাতিল</button>
          <button className="btn bp" style={{flex:2,justifyContent:"center"}} disabled={saving} onClick={save}>{saving?"⏳ সেভ হচ্ছে...":"💾 সেভ করুন"}</button>
        </div>
      </div>
    </div>
  );
}

function RenameTab({push,tick}){
  const[sheet,setSheet]=useState("QBank");
  const[type,setType]=useState("subject");
  const{data:raw,loading}=useFB(sheet,tick);
  const[renameTarget,setRenameTarget]=useState(null);
  const[newName,setNewName]=useState("");
  const[renaming,setRenaming]=useState(false);

  const allQ=useMemo(()=>toArr(raw),[raw]);

  const list=useMemo(()=>{
    const map={};
    allQ.forEach(q=>{
      let key="";
      if(type==="subject")key=(q.Subject||q.subject||"").trim();
      else if(type==="topic")key=(q.Topic||q.topic||"").trim()||(q.Sub_topic||q.sub_topic||"").split(" > ")[0].trim();
      else key=(q.Sub_topic||q.sub_topic||"").trim();
      if(key)map[key]=(map[key]||0)+1;
    });
    return Object.entries(map).sort((a,b)=>b[1]-a[1]);
  },[allQ,type]);

  const doRename=async()=>{
    if(!newName.trim()||!renameTarget){push("warn","নতুন নাম দিন","");return;}
    if(newName.trim()===renameTarget.name){push("info","একই নাম","");return;}
    setRenaming(true);
    try{
      const oldName=renameTarget.name;
      const nName=newName.trim();

      const affected=allQ.filter(q=>{
        if(type==="subject")return(q.Subject||q.subject||"").trim()===oldName;
        if(type==="topic")return(q.Topic||q.topic||"").trim()===oldName||(q.Sub_topic||q.sub_topic||"").split(" > ")[0].trim()===oldName;
        return(q.Sub_topic||q.sub_topic||"").trim()===oldName;
      });

      // Per-item PATCH — Firebase REST does not support slash-key multi-path
      for(const q of affected){
        const fkey=q._fbKey;if(!fkey)continue;
        if(type==="subject"){
          await fbPatch(`${sheet}/${fkey}`,{Subject:nName});
        } else if(type==="topic"){
          const patch={Topic:nName};
          const st=q.Sub_topic||q.sub_topic||"";
          if(st.includes(" > ")){const parts=st.split(" > ");if(parts[0].trim()===oldName)patch.Sub_topic=`${nName} > ${parts.slice(1).join(" > ")}`;}
          await fbPatch(`${sheet}/${fkey}`,patch);
        } else {
          await fbPatch(`${sheet}/${fkey}`,{Sub_topic:nName});
        }
      }
      invalidate(sheet);

      // GAS renameField skipped — Firebase already updated above; GAS syncToFirebase would overwrite structure
      push("success","✅ Rename সম্পন্ন!",`"${oldName}" → "${nName}" · ${affected.length}টি`);
      setRenameTarget(null);setNewName("");
    }catch(e){push("error","Rename ব্যর্থ",e.message);}
    setRenaming(false);
  };

  return(
    <>
      <div style={{display:"flex",gap:6,marginBottom:8}}>
        {["QBank","Quiz","Study"].map(s=>(
          <button key={s} className={`ftab${sheet===s?" on":""}`} onClick={()=>setSheet(s)}>{s}</button>
        ))}
      </div>
      <div className="atabs" style={{marginBottom:10}}>
        <button className={`atab${type==="subject"?" on":""}`} onClick={()=>setType("subject")}>📚 Subject</button>
        <button className={`atab${type==="topic"?" on":""}`} onClick={()=>setType("topic")}>📂 Topic</button>
        <button className={`atab${type==="subtopic"?" on":""}`} onClick={()=>setType("subtopic")}>📌 Subtopic</button>
      </div>
      <div style={{fontSize:11,color:C.muted,marginBottom:10}}>{loading?"⏳":`${list.length}টি · ক্লিক করে রিনেম করুন`}</div>
      {loading&&!raw?[...Array(4)].map((_,i)=><div key={i} className="sk" style={{height:46}}/>):
       list.length===0?<div className="empty"><div className="ei">📂</div><p>কিছু নেই</p></div>:
       list.map(([name,count])=>(
        <div key={name} className="rename-row" onClick={()=>{setRenameTarget({name,count});setNewName(name);}}>
          <div className="rename-name">{name}</div>
          <div className="rename-count">{count}টি</div>
          <button className="btn" style={{padding:"4px 10px",fontSize:10,background:C.accent+"20",color:C.accent,border:`1px solid ${C.accent}30`}}>✏️</button>
        </div>
       ))
      }
      {renameTarget&&(
        <RenameModal
          type={type}
          target={renameTarget}
          newName={newName}
          setNewName={setNewName}
          onCancel={()=>{setRenameTarget(null);setNewName("");}}
          onRename={doRename}
          renaming={renaming}
        />
      )}
    </>
  );
}

function RenameModal({type,target,newName,setNewName,onCancel,onRename,renaming}){
  useModalBack(onCancel);
  return(
    <div className="ovl">
      <div className="modal">
        <div className="mh"/>
        <div style={{fontSize:13,fontWeight:700,marginBottom:4}}>✏️ Rename {type}</div>
        <div style={{background:`${C.yellow}12`,border:`1px solid ${C.yellow}30`,borderRadius:9,padding:"8px 11px",marginBottom:12,fontSize:11}}>
          <span style={{color:C.yellow,fontWeight:700}}>⚠️ </span>
          <span style={{color:C.muted}}><b style={{color:C.text}}>{target.count}টি</b> প্রশ্নে Firebase ও Sheet-এ আপডেট হবে।</span>
        </div>
        <div className="fld"><label>পুরোনো নাম</label><div style={{background:C.panel,border:`1px solid ${C.border}`,borderRadius:9,padding:"9px 12px",fontSize:13,color:C.muted}}>{target.name}</div></div>
        <div className="fld"><label>নতুন নাম</label><input className="inp" value={newName} onChange={e=>setNewName(e.target.value)}/></div>
        <div style={{display:"flex",gap:7}}>
          <button className="btn bg" style={{flex:1,justifyContent:"center"}} onClick={onCancel} disabled={renaming}>বাতিল</button>
          <button className="btn bp" style={{flex:2,justifyContent:"center"}} onClick={onRename} disabled={renaming}>{renaming?"⏳ আপডেট হচ্ছে...":"✅ Rename"}</button>
        </div>
      </div>
    </div>
  );
}

/* ══════════ AUDIENCE TAG RENAME TAB ══════════ */
function AudienceTagRenameTab({push,tick}){
  const SHEETS=["QBank","Quiz","Study"];

  // Load all 3 sheets
  const{data:qbRaw,loading:qbL}=useFB("QBank",tick);
  const{data:qzRaw,loading:qzL}=useFB("Quiz",tick);
  const{data:stRaw,loading:stL}=useFB("Study",tick);

  const loading=qbL||qzL||stL;

  const[renameTarget,setRenameTarget]=useState(null);
  const[newName,setNewName]=useState("");
  const[renaming,setRenaming]=useState(false);

  // Collect all unique AudienceTags across all sheets with count & which sheets they appear in
  const tagList=useMemo(()=>{
    const map={}; // tag -> {count, sheets: {QBank:n, Quiz:n, Study:n}}
    [[qbRaw,"QBank"],[qzRaw,"Quiz"],[stRaw,"Study"]].forEach(([raw,sheet])=>{
      toArr(raw).forEach(q=>{
        const tag=(q.AudienceTags||q.audienceTags||q.audience_tags||"").trim();
        if(!tag)return;
        if(!map[tag])map[tag]={count:0,sheets:{}};
        map[tag].count++;
        map[tag].sheets[sheet]=(map[tag].sheets[sheet]||0)+1;
      });
    });
    return Object.entries(map).sort((a,b)=>b[1].count-a[1].count);
  },[qbRaw,qzRaw,stRaw]);

  const doRename=async()=>{
    if(!newName.trim()||!renameTarget){push("warn","নতুন নাম দিন","");return;}
    if(newName.trim()===renameTarget.tag){push("info","একই নাম","");return;}
    setRenaming(true);
    try{
      const oldTag=renameTarget.tag;
      const nTag=newName.trim();
      let totalUpdated=0;

      for(const sheet of SHEETS){
        const raw=sheet==="QBank"?qbRaw:sheet==="Quiz"?qzRaw:stRaw;
        const allQ=toArr(raw);
        const affected=allQ.filter(q=>{
          const t=(q.AudienceTags||q.audienceTags||q.audience_tags||"").trim();
          return t===oldTag;
        });
        if(affected.length===0)continue;

        // Per-item PATCH — Firebase REST does not support slash-key multi-path
        for(const q of affected){
          const fkey=q._fbKey;if(!fkey)continue;
          const fieldKey=q.AudienceTags!=null?"AudienceTags":q.audienceTags!=null?"audienceTags":"audience_tags";
          await fbPatch(`${sheet}/${fkey}`,{[fieldKey]:nTag});
          totalUpdated++;
        }

        // Also invalidate the sheet cache
        invalidate(sheet);
      }

      push("success","✅ Audience Tag Rename সম্পন্ন!",`"${oldTag}" → "${nTag}" · ${totalUpdated}টি কন্টেন্ট`);
      setRenameTarget(null);
      setNewName("");
    }catch(e){push("error","Rename ব্যর্থ",e.message);}
    setRenaming(false);
  };

  return(
    <>
      {/* Info banner */}
      <div style={{background:`${C.accent}12`,border:`1px solid ${C.accent}30`,borderRadius:10,padding:"9px 12px",marginBottom:12,fontSize:11}}>
        <div style={{fontWeight:700,color:C.accent,marginBottom:3}}>🎯 Audience Tag Rename</div>
        <div style={{color:C.muted,lineHeight:1.6}}>
          QBank, Quiz ও Study — তিনটি শিটে একসাথে AudienceTags আপডেট হবে।
          <br/>ব্যবহারকারীর <b style={{color:C.text}}>classLevel</b>-এর সাথে মিলতে হবে।
        </div>
      </div>

      <div style={{fontSize:11,color:C.muted,marginBottom:10}}>
        {loading?"⏳ লোড হচ্ছে...":`${tagList.length}টি Audience Tag পাওয়া গেছে`}
      </div>

      {/* Tag list */}
      {loading?[...Array(4)].map((_,i)=><div key={i} className="sk" style={{height:56,marginBottom:7}}/>):
       tagList.length===0?
        <div className="empty"><div className="ei">🎯</div><p>কোনো AudienceTags নেই</p></div>:
        tagList.map(([tag,info])=>(
          <div key={tag} style={{
            background:C.panel,border:`1px solid ${C.border}`,borderRadius:11,
            padding:"11px 12px",marginBottom:7,
            display:"flex",alignItems:"center",gap:10
          }}>
            <div style={{flex:1,minWidth:0}}>
              <div style={{fontWeight:700,fontSize:13,marginBottom:3}}>{tag}</div>
              <div style={{display:"flex",gap:5,flexWrap:"wrap"}}>
                {Object.entries(info.sheets).map(([sh,n])=>(
                  <span key={sh} style={{
                    fontSize:9,fontWeight:700,padding:"1px 6px",borderRadius:6,
                    background:sh==="QBank"?`${C.green}20`:sh==="Quiz"?`${C.accent}20`:`${C.yellow}20`,
                    color:sh==="QBank"?C.green:sh==="Quiz"?C.accent:C.yellow,
                    border:`1px solid ${sh==="QBank"?C.green:sh==="Quiz"?C.accent:C.yellow}40`
                  }}>{sh}: {n}টি</span>
                ))}
              </div>
            </div>
            <div style={{fontWeight:700,fontSize:15,color:C.muted,minWidth:28,textAlign:"right"}}>{info.count}</div>
            <button
              className="btn"
              style={{padding:"5px 11px",fontSize:11,background:C.accent+"22",color:C.accent,border:`1px solid ${C.accent}33`,flexShrink:0}}
              onClick={()=>{setRenameTarget({tag,count:info.count});setNewName(tag);}}
            >✏️ Rename</button>
          </div>
        ))
      }

      {/* Rename Modal */}
      {renameTarget&&(
        <AudienceRenameModal
          target={renameTarget}
          newName={newName}
          setNewName={setNewName}
          onCancel={()=>{setRenameTarget(null);setNewName("");}}
          onRename={doRename}
          renaming={renaming}
        />
      )}
    </>
  );
}

function AudienceRenameModal({target,newName,setNewName,onCancel,onRename,renaming}){
  useModalBack(onCancel);
  return(
    <div className="ovl">
      <div className="modal">
        <div className="mh"/>
        <div style={{fontSize:14,fontWeight:700,marginBottom:4}}>🎯 Audience Tag Rename</div>

        {/* Warning */}
        <div style={{background:`${C.yellow}12`,border:`1px solid ${C.yellow}30`,borderRadius:9,padding:"8px 11px",marginBottom:12,fontSize:11}}>
          <span style={{color:C.yellow,fontWeight:700}}>⚠️ </span>
          <span style={{color:C.muted}}>
            <b style={{color:C.text}}>{target.count}টি</b> কন্টেন্টে Firebase-এ আপডেট হবে।
            <br/>ব্যবহারকারীর <b style={{color:C.text}}>classLevel</b>-এর সাথে মিল রাখুন।
          </span>
        </div>

        {/* Old name (readonly) */}
        <div className="fld">
          <label>পুরোনো Tag</label>
          <div style={{background:C.panel,border:`1px solid ${C.border}`,borderRadius:9,padding:"9px 12px",fontSize:13,color:C.muted,fontFamily:"monospace"}}>
            {target.tag}
          </div>
        </div>

        {/* New name input */}
        <div className="fld">
          <label>নতুন Tag</label>
          <input
            className="inp"
            value={newName}
            onChange={e=>setNewName(e.target.value)}
            placeholder="যেমন: Masters 1"
            style={{fontFamily:"monospace"}}
            autoFocus
          />
          {newName&&newName!==target.tag&&(
            <div style={{fontSize:10,color:C.green,marginTop:4,fontWeight:600}}>
              ✅ "{target.tag}" → "{newName}"
            </div>
          )}
        </div>

        {/* Hint */}
        <div style={{background:`${C.accent}10`,border:`1px solid ${C.accent}25`,borderRadius:8,padding:"7px 10px",marginBottom:12,fontSize:10,color:C.muted}}>
          💡 <b style={{color:C.text}}>classLevel মানগুলো:</b> Masters 1, Masters 2, Honours 1, Honours 2, Honours 3, Honours 4, Class 12, Job
        </div>

        <div style={{display:"flex",gap:7}}>
          <button className="btn bg" style={{flex:1,justifyContent:"center"}} onClick={onCancel} disabled={renaming}>বাতিল</button>
          <button className="btn bp" style={{flex:2,justifyContent:"center"}} onClick={onRename} disabled={renaming||!newName.trim()||newName.trim()===target.tag}>
            {renaming?"⏳ আপডেট হচ্ছে...":"✅ Rename করুন"}
          </button>
        </div>
      </div>
    </div>
  );
}

function DeleteTab({push,tick}){
  const[sheet,setSheet]=useState("QBank");
  const[type,setType]=useState("subject");
  const{data:raw,loading}=useFB(sheet,tick);
  const[delTarget,setDelTarget]=useState(null);
  const[delLoading,setDelLoading]=useState(false);

  const allQ=useMemo(()=>toArr(raw),[raw]);

  const groups=useMemo(()=>{
    const map={};
    allQ.forEach(q=>{
      let key="";
      if(type==="subject")key=(q.Subject||q.subject||"").trim();
      else if(type==="topic")key=(q.Topic||q.topic||"").trim()||(q.Sub_topic||q.sub_topic||"").split(" > ")[0].trim();
      else key=(q.Sub_topic||q.sub_topic||"").trim();
      if(key)map[key]=(map[key]||[]).concat(q);
    });
    return Object.entries(map).sort((a,b)=>b[1].length-a[1].length);
  },[allQ,type]);

  const doBulkDelete=async()=>{
    if(!delTarget)return;
    setDelLoading(true);
    try{
      const[groupName,qs]=delTarget;
      // Delete per item — Firebase REST root PATCH with slash-key does not work
      for(const q of qs){
        if(q._fbKey) await fbDelete(`${sheet}/${q._fbKey}`);
      }
      invalidate(sheet);
      // gasBg deleteByIds skipped — Firebase already deleted above
      push("success","🗑️ Bulk Delete!",`"${groupName}" · ${qs.length}টি মুছে গেছে`);
      setDelTarget(null);
    }catch(e){push("error","Delete ব্যর্থ",e.message);}
    setDelLoading(false);
  };

  return(
    <>
      <div style={{display:"flex",gap:6,marginBottom:8}}>
        {["QBank","Quiz","Study"].map(s=>(
          <button key={s} className={`ftab${sheet===s?" on":""}`} onClick={()=>setSheet(s)}>{s}</button>
        ))}
      </div>
      <div className="atabs" style={{marginBottom:8}}>
        <button className={`atab${type==="subject"?" on":""}`} onClick={()=>setType("subject")}>📚 Subject</button>
        <button className={`atab${type==="topic"?" on":""}`} onClick={()=>setType("topic")}>📂 Topic</button>
        <button className={`atab${type==="subtopic"?" on":""}`} onClick={()=>setType("subtopic")}>📌 Subtopic</button>
      </div>
      <div style={{background:"#ef444412",border:"1px solid #ef444330",borderRadius:9,padding:"8px 11px",marginBottom:10,fontSize:11,color:C.red}}>⚠️ পুরো Subject/Topic ডিলিট — ভেতরের সব প্রশ্ন মুছে যাবে।</div>
      {loading&&!raw?[...Array(4)].map((_,i)=><div key={i} className="sk" style={{height:46}}/>):
       groups.length===0?<div className="empty"><div className="ei">📂</div><p>কিছু নেই</p></div>:
       groups.map(([name,qs])=>(
        <div key={name} className="rename-row">
          <div className="rename-name">{name}</div>
          <div className="rename-count">{qs.length}টি</div>
          <button className="btn" style={{padding:"4px 10px",fontSize:10,background:C.red+"22",color:C.red,border:`1px solid ${C.red}33`}} onClick={()=>setDelTarget([name,qs])}>🗑️</button>
        </div>
       ))
      }
      {delTarget&&<DeleteWarningModal
        title={`"${delTarget[0]}" ডিলিট?`}
        description={`${delTarget[1].length}টি প্রশ্ন Firebase ও Sheet থেকে মুছে যাবে।`}
        onConfirm={doBulkDelete} onCancel={()=>setDelTarget(null)} loading={delLoading}
      />}
    </>
  );
}

/* ══════════ SEARCH ══════════ */
function SearchPage({push,onDetail}){
  const[q,setQ]=useState("");
  const[results,setResults]=useState(null);
  const[searching,setSearching]=useState(false);
  const deb=useRef(null);

  const doSearch=useCallback(async query=>{
    if(!query||query.length<2){setResults(null);return;}
    setSearching(true);
    const qlo=query.toLowerCase();
    try{
      const[qbankRaw,usersRaw]=await Promise.all([loadPath("QBank"),loadPath("Users")]);
      const srch=(raw,tab)=>toArr(raw).filter(q2=>{
        return[(q2.Question||q2.question||""),(q2.Subject||q2.subject||""),(q2.Correct||q2.correct||"")].join(" ").toLowerCase().includes(qlo);
      }).slice(0,8).map(q2=>({...q2,_tab:tab}));
      const uRes=toArr(usersRaw).filter(u=>(u.Name||u.name||"").toLowerCase().includes(qlo)||(u.Phone||u.phone||"").toLowerCase().includes(qlo)).slice(0,5);
      setResults({questions:srch(qbankRaw,"QBank"),users:uRes});
    }catch(e){}
    setSearching(false);
  },[]);

  const onIn=v=>{setQ(v);clearTimeout(deb.current);deb.current=setTimeout(()=>doSearch(v),400);};
  const tot=(results?.questions?.length||0)+(results?.users?.length||0);

  return(
    <div className="page">
      <div className="sw" style={{marginBottom:12}}>
        <span className="si">🔍</span>
        <input className="inp" placeholder="নাম, ফোন, প্রশ্ন..." value={q} onChange={e=>onIn(e.target.value)}/>
      </div>
      {searching&&<div style={{textAlign:"center",padding:"20px 0",color:C.muted,fontSize:12}}>⏳</div>}
      {!searching&&results&&<div style={{fontSize:11,color:C.muted,marginBottom:8}}>{tot}টি ফলাফল</div>}
      {results?.users?.length>0&&<>
        <div className="slb">👥 স্টুডেন্ট</div>
        {results.users.map((u,i)=>{
          const nm=u.Name||u.name||"অজানা",ph=u.Phone||u.phone||"—";
          const st=(u.Status||u.status||"inactive").toLowerCase();
          return(
            <div key={i} className="sri" onClick={()=>onDetail(u)}>
              <div className="av sm">{initials(nm)}</div>
              <div style={{flex:1,minWidth:0}}><div style={{fontWeight:600,fontSize:12}}>{nm}</div><div style={{fontSize:10,color:C.muted}}>📱 {ph}</div></div>
              <span className={`pill ${st==="active"?"pa":"pi"}`}>{st==="active"?"✅":"🔴"}</span>
            </div>
          );
        })}
      </>}
      {results?.questions?.length>0&&<>
        <div className="slb">❓ প্রশ্ন</div>
        {results.questions.map((q2,i)=>(
          <div key={i} className="sri" style={{cursor:"default"}}>
            <span className="stag">{q2._tab}</span>
            <div style={{flex:1,minWidth:0}}>
              <div style={{fontSize:12,fontWeight:600,lineHeight:1.4}}>{(q2.Question||q2.question||"").slice(0,80)}</div>
              {(q2.Correct||q2.correct)&&<div style={{fontSize:10,color:C.green,marginTop:1}}>✅ {q2.Correct||q2.correct}</div>}
            </div>
          </div>
        ))}
      </>}
      {!searching&&results&&tot===0&&<div className="empty"><div className="ei">🔍</div><p>পাওয়া যায়নি</p></div>}
      {!results&&<div className="empty" style={{paddingTop:28}}><div className="ei">🔍</div><p style={{fontSize:12}}>সব কিছু খুঁজুন</p></div>}
    </div>
  );
}

/* ══════════ NOTIFY PAGE ══════════ */
function NotifyPage({push}){
  const[title,setTitle]=useState("");
  const[body,setBody]=useState("");
  const[sending,setSending]=useState(false);
  const[hist,setHist]=useState([]);

  const send=async()=>{
    if(!title||!body){push("warn","তথ্য দিন","");return;}
    setSending(true);
    try{
      const raw=await loadPath("Users");
      const users=toArr(raw);
      const active=users.filter(u=>(u.Status||u.status||"").toLowerCase()==="active");
      const ts=nowTs();
      const notifKey=`broadcast_${Date.now()}`;
      await Promise.all(active.map(u=>{
        const phK=phoneKey(u.Phone||u.phone||"");
        if(!phK)return Promise.resolve();
        return fbSet(`Notifications/${phK}/${notifKey}`,{type:"broadcast",title,body,time:ts,read:false});
      }));
      // FCM: GAS এ পাঠাও (GAS নিজে FCMTokens থেকে পড়ে পাঠাবে)
      let fcmSent=0;
      try{
        const r=await Promise.race([
          gasCall({action:"broadcastNotification",title:encodeURIComponent(title),body:encodeURIComponent(body)}),
          new Promise((_,rej)=>setTimeout(()=>rej(new Error("timeout")),10000))
        ]);
        fcmSent=r?.fcm?.sent||0;
      }catch(_){}
      push("success","📣 পাঠানো হয়েছে!",`Notification: ${active.length}জন · FCM: ${fcmSent}জন`);
      setHist(p=>[{title,body,time:ts,count:active.length},...p.slice(0,9)]);
      setTitle("");setBody("");
    }catch(e){push("error","ব্যর্থ",String(e?.message||e||""));}
    setSending(false);
  };

  return(
    <div className="page">
      <div className="card">
        <div className="ct">📣 সবাইকে Broadcast</div>
        <div className="fld"><label>শিরোনাম</label><input className="inp" placeholder="Title..." value={title} onChange={e=>setTitle(e.target.value)}/></div>
        <div className="fld"><label>বার্তা</label><textarea className="ta" placeholder="Message..." value={body} onChange={e=>setBody(e.target.value)}/></div>
        <button className="btn bp bb" onClick={send} disabled={sending}>{sending?"⏳ পাঠানো হচ্ছে...":"📣 সবাইকে পাঠান"}</button>
      </div>
      {hist.length>0&&<div className="card"><div className="ct">ইতিহাস</div>{hist.map((h,i)=>(
        <div key={i} className="nr">
          <div className={`nd ${i===0?"n":"o"}`}/>
          <div className="nc"><div className="nt">{h.title}</div><div className="ns">{h.body?.slice(0,55)}<span style={{color:C.accent}}> · {h.count}জন</span></div></div>
          <div className="ntm">{h.time}</div>
        </div>
      ))}</div>}
    </div>
  );
}

function NotifyModal({user,onClose,push,inline}){
  useModalBack(inline?()=>{}:onClose);
  const[title,setTitle]=useState("");
  const[body,setBody]=useState("");
  const[sending,setSending]=useState(false);
  const nm=user.Name||user.name||"স্টুডেন্ট";

  const send=async()=>{
    if(!title||!body)return;
    setSending(true);
    try{
      const phone=(user.Phone||user.phone||"").toString();
      const phK=phoneKey(phone);
      await fbSet(`Notifications/${phK}/notif_${Date.now()}`,{type:"personal",title,body,time:nowTs(),read:false});
      let fcmOk=false;
      try{
        const fr=await Promise.race([
          gasCall({action:"personalNotify",phone,title:encodeURIComponent(title),body:encodeURIComponent(body)}),
          new Promise((_,rej)=>setTimeout(()=>rej(new Error("timeout")),8000))
        ]);
        fcmOk=!fr?.fcm?.error;
      }catch(_){}
      push("success","✅ পাঠানো হয়েছে",(fcmOk?"📲 FCM ✓ ":"📲 FCM ✗ ")+nm);
      if(!inline)onClose();
    }catch(e){push("error","ব্যর্থ",String(e?.message||e||""));}
    setSending(false);
  };

  if(inline)return(
    <div className="card">
      <div className="ct">📣 ব্যক্তিগত নোটিফিকেশন</div>
      <div className="fld"><label>শিরোনাম</label><input className="inp" placeholder="Title..." value={title} onChange={e=>setTitle(e.target.value)}/></div>
      <div className="fld"><label>বার্তা</label><textarea className="ta" placeholder="Message..." value={body} onChange={e=>setBody(e.target.value)}/></div>
      <button className="btn bp bb" onClick={send} disabled={sending}>{sending?"⏳":"📨 পাঠান"}</button>
    </div>
  );
  return(
    <div className="ovl">
      <div className="modal">
        <div className="mh"/>
        <div className="mt">📣 {nm}</div>
        <div style={{fontSize:11,color:C.muted,marginBottom:11}}>📱 {user.Phone||user.phone}</div>
        <div className="fld"><label>শিরোনাম</label><input className="inp" placeholder="Title..." value={title} onChange={e=>setTitle(e.target.value)}/></div>
        <div className="fld"><label>বার্তা</label><textarea className="ta" placeholder="Message..." value={body} onChange={e=>setBody(e.target.value)}/></div>
        <div style={{display:"flex",gap:7}}>
          <button className="btn bg" style={{flex:1,justifyContent:"center"}} onClick={onClose}>বাতিল</button>
          <button className="btn bp" style={{flex:1,justifyContent:"center"}} onClick={send} disabled={sending}>{sending?"⏳":"📨 পাঠান"}</button>
        </div>
      </div>
    </div>
  );
}

/* ══════════ ROOT APP ══════════ */

/* ══════════ TECHNIQUES PAGE ══════════ */
function TechniquesPage({push,tick}){
  const{data:raw,loading}=useFB("UserTechniques",tick);
  const[tab,setTab]=useState("pending");
  const[busy,setBusy]=useState(null);
  const[detail,setDetail]=useState(null);
  const[done,setDone]=useState(new Set());

  // Flatten nested structure: { questionId: { pushKey: {...} } }
  const allTechniques=useMemo(()=>{
    if(!raw||typeof raw!=="object")return[];
    const list=[];
    Object.entries(raw).forEach(([qId,entries])=>{
      if(!entries||typeof entries!=="object")return;
      Object.entries(entries).forEach(([pushKey,data])=>{
        if(!data||typeof data!=="object")return;
        list.push({...data,_qId:qId,_pushKey:pushKey,_path:`UserTechniques/${qId}/${pushKey}`});
      });
    });
    return list.sort((a,b)=>(b.timestamp||0)-(a.timestamp||0));
  },[raw]);

  const filtered=useMemo(()=>
    allTechniques.filter(t=>{
      if(done.has(t._pushKey))return false;
      if(tab==="pending")return t.isPublic&&(t.status==="pending"||!t.status);
      if(tab==="approved")return t.isPublic&&t.status==="approved";
      if(tab==="rejected")return t.isPublic&&t.status==="rejected";
      return true; // "all"
    })
  ,[allTechniques,tab,done]);

  const pendingCount=useMemo(()=>
    allTechniques.filter(t=>!done.has(t._pushKey)&&t.isPublic&&(t.status==="pending"||!t.status)).length
  ,[allTechniques,done]);

  const updateStatus=async(t,status)=>{
    const key=t._pushKey;
    setBusy(key);
    try{
      await fbPatch(`UserTechniques/${t._qId}/${key}`,{status});
      invalidate("UserTechniques");
      setDone(p=>new Set([...p,key]));
      push("success",status==="approved"?"✅ Approved!":"❌ Rejected!",t.userName||"ব্যবহারকারী");
    }catch(e){push("error","ব্যর্থ",e.message);}
    setBusy(null);
  };

  const deleteT=async(t)=>{
    setBusy(t._pushKey);
    try{
      await fbDelete(`UserTechniques/${t._qId}/${t._pushKey}`);
      invalidate("UserTechniques");
      setDone(p=>new Set([...p,t._pushKey]));
      push("success","🗑️ ডিলিট!","");
    }catch(e){push("error","ব্যর্থ",e.message);}
    setBusy(null);
  };

  const tsFormat=ts=>{
    if(!ts)return"—";
    const d=new Date(parseInt(ts));
    return d.toLocaleString("bn-BD",{timeZone:"Asia/Dhaka",dateStyle:"short",timeStyle:"short"});
  };

  return(
    <div className="page">
      {/* Summary row */}
      <div className="sg" style={{marginBottom:10}}>
        <div className="sc tb" data-icon="⏳">
          <div className="sl">পেন্ডিং</div>
          <div className="sv sv-b">{pendingCount}</div>
        </div>
        <div className="sc tg" data-icon="✅">
          <div className="sl">মোট</div>
          <div className="sv sv-g">{allTechniques.filter(t=>t.isPublic).length}</div>
        </div>
      </div>

      {/* Tabs */}
      <div className="atabs" style={{marginBottom:10}}>
        {[["pending","⏳ পেন্ডিং"],["approved","✅ Approved"],["rejected","❌ Rejected"],["all","📋 সব"]].map(([v,l])=>(
          <button key={v} className={`atab${tab===v?" on":""}`} onClick={()=>setTab(v)}>{l}
            {v==="pending"&&pendingCount>0&&<span style={{background:C.red,color:"#fff",borderRadius:"50%",fontSize:8,padding:"1px 4px",marginLeft:3}}>{pendingCount}</span>}
          </button>
        ))}
      </div>

      <div style={{fontSize:11,color:C.muted,marginBottom:8,display:"flex",justifyContent:"space-between"}}>
        <span>{loading?"⏳":`${filtered.length}টি`}</span>
        {loading&&<span>⏳</span>}
      </div>

      {/* List */}
      {loading&&!raw?[...Array(3)].map((_,i)=><div key={i} className="sk"/>):
       filtered.length===0?<div className="empty"><div className="ei">🧠</div><p>{tab==="pending"?"কোনো পেন্ডিং নেই 🎉":"কিছু নেই"}</p></div>:
       filtered.map((t,i)=>(
        <div key={t._pushKey||i} style={{
          background:C.card,border:`1px solid ${C.border}`,borderRadius:12,
          padding:12,marginBottom:8,
          borderLeft:`3px solid ${t.status==="approved"?C.green:t.status==="rejected"?C.red:C.yellow}`
        }}>
          {/* Header */}
          <div style={{display:"flex",alignItems:"center",gap:6,marginBottom:7,flexWrap:"wrap"}}>
            <div style={{width:28,height:28,borderRadius:"50%",background:`linear-gradient(135deg,${C.accent},${C.purple})`,display:"flex",alignItems:"center",justifyContent:"center",fontSize:11,fontWeight:700,color:"#fff",flexShrink:0}}>
              {(t.userName||"?").slice(0,1).toUpperCase()}
            </div>
            <div style={{flex:1,minWidth:0}}>
              <div style={{fontWeight:700,fontSize:12}}>{t.userName||"ব্যবহারকারী"}</div>
              <div style={{fontSize:10,color:C.muted}}>📱 {t.userId||"—"}</div>
            </div>
            <div style={{textAlign:"right"}}>
              <span style={{
                fontSize:9,fontWeight:700,padding:"2px 7px",borderRadius:6,
                background:t.status==="approved"?`${C.green}20`:t.status==="rejected"?`${C.red}20`:`${C.yellow}20`,
                color:t.status==="approved"?C.green:t.status==="rejected"?C.red:C.yellow,
                border:`1px solid ${t.status==="approved"?C.green:t.status==="rejected"?C.red:C.yellow}40`
              }}>
                {t.status==="approved"?"✅ Approved":t.status==="rejected"?"❌ Rejected":"⏳ Pending"}
              </span>
            </div>
          </div>

          {/* Question ID */}
          <div style={{fontSize:10,color:C.accent,marginBottom:5,fontWeight:600}}>
            ❓ প্রশ্ন ID: {t._qId||"—"}
          </div>

          {/* Technique text */}
          <div style={{
            background:C.panel,border:`1px solid ${C.border}`,borderRadius:8,
            padding:"8px 10px",fontSize:12,lineHeight:1.6,color:C.text,marginBottom:7
          }}>
            💡 {t.text||"(খালি)"}
          </div>

          <div style={{fontSize:10,color:C.muted,marginBottom:8}}>
            🕐 {tsFormat(t.timestamp)}
          </div>

          {/* Action buttons */}
          <div style={{display:"flex",gap:6}}>
            {(t.status==="pending"||!t.status)&&<>
              <button
                className="btn"
                style={{flex:2,justifyContent:"center",background:C.green,color:"#fff",fontSize:11}}
                disabled={!!busy}
                onClick={()=>updateStatus(t,"approved")}
              >
                {busy===t._pushKey?"⏳...":"✅ Approve"}
              </button>
              <button
                className="btn"
                style={{flex:2,justifyContent:"center",background:C.red,color:"#fff",fontSize:11}}
                disabled={!!busy}
                onClick={()=>updateStatus(t,"rejected")}
              >
                {busy===t._pushKey?"⏳...":"❌ Reject"}
              </button>
            </>}
            {t.status==="approved"&&(
              <button
                className="btn bg"
                style={{flex:2,justifyContent:"center",fontSize:11}}
                disabled={!!busy}
                onClick={()=>updateStatus(t,"rejected")}
              >❌ Reject করুন</button>
            )}
            {t.status==="rejected"&&(
              <button
                className="btn"
                style={{flex:2,justifyContent:"center",background:C.green,color:"#fff",fontSize:11}}
                disabled={!!busy}
                onClick={()=>updateStatus(t,"approved")}
              >✅ Re-Approve</button>
            )}
            <button
              className="btn"
              style={{flex:1,justifyContent:"center",background:C.red+"22",color:C.red,border:`1px solid ${C.red}33`,fontSize:11}}
              disabled={!!busy}
              onClick={()=>deleteT(t)}
            >🗑️</button>
          </div>
        </div>
       ))
      }
    </div>
  );
}

const NAV=[
  {id:"dashboard",icon:"📊",label:"Dashboard"},
  {id:"signups",  icon:"🆕",label:"সাইনআপ",badge:true},
  {id:"students", icon:"👥",label:"Students"},
  {id:"reports",  icon:"🚨",label:"Reports",badge:true},
  {id:"content",  icon:"📋",label:"Content"},
  {id:"techniques",icon:"🧠",label:"টেকনিক",badge:true},
  {id:"notify",   icon:"📣",label:"Notify"},
];

/* ══════════ LOGIN SCREEN ══════════ */
function LoginScreen({onLogin}){
  const[email,setEmail]=useState(localStorage.getItem("fb_email")||"");
  const[pass,setPass]=useState("");
  const[loading,setLoading]=useState(false);
  const[err,setErr]=useState("");

  // Auto-login if saved credentials exist
  useEffect(()=>{
    const savedEmail=localStorage.getItem("fb_email");
    const savedPass=localStorage.getItem("fb_pass_enc");
    if(savedEmail&&savedPass){
      setLoading(true);
      signInWithEmail(savedEmail,atob(savedPass))
        .then(()=>onLogin())
        .catch(()=>{ localStorage.removeItem("fb_email");localStorage.removeItem("fb_pass_enc");setLoading(false); });
    }
  },[]);

  const doLogin=async()=>{
    if(!email||!pass){setErr("Email ও Password দিন");return;}
    setLoading(true);setErr("");
    try{
      await signInWithEmail(email,pass);
      onLogin();
    }catch(e){setErr(e.message||"Login ব্যর্থ");setLoading(false);}
  };

  return(
    <div style={{minHeight:"100dvh",display:"flex",alignItems:"center",justifyContent:"center",background:"#06080f",padding:24}}>
      <div style={{width:"100%",maxWidth:360}}>
        <div style={{textAlign:"center",marginBottom:32}}>
          <div style={{fontSize:42,marginBottom:8}}>📊</div>
          <div style={{fontSize:20,fontWeight:700,color:"#e2e8f0"}}>Smart Study Admin</div>
          <div style={{fontSize:12,color:"#4b5e7a",marginTop:4}}>Firebase অ্যাডমিন প্যানেল</div>
        </div>
        <div style={{background:"#0c1220",border:"1px solid #16253d",borderRadius:16,padding:20}}>
          <div style={{marginBottom:12}}>
            <label style={{display:"block",fontSize:10,fontWeight:700,color:"#4b5e7a",letterSpacing:".8px",marginBottom:4,textTransform:"uppercase"}}>Email</label>
            <input
              style={{background:"#0e1a2e",border:"1px solid #16253d",borderRadius:9,padding:"10px 12px",color:"#e2e8f0",fontFamily:"inherit",fontSize:13,width:"100%",outline:"none",boxSizing:"border-box"}}
              type="email" placeholder="admin@example.com"
              value={email} onChange={e=>setEmail(e.target.value)}
              onKeyDown={e=>e.key==="Enter"&&doLogin()}
            />
          </div>
          <div style={{marginBottom:16}}>
            <label style={{display:"block",fontSize:10,fontWeight:700,color:"#4b5e7a",letterSpacing:".8px",marginBottom:4,textTransform:"uppercase"}}>Password</label>
            <input
              style={{background:"#0e1a2e",border:"1px solid #16253d",borderRadius:9,padding:"10px 12px",color:"#e2e8f0",fontFamily:"inherit",fontSize:13,width:"100%",outline:"none",boxSizing:"border-box"}}
              type="password" placeholder="••••••••"
              value={pass} onChange={e=>setPass(e.target.value)}
              onKeyDown={e=>e.key==="Enter"&&doLogin()}
            />
          </div>
          {err&&<div style={{background:"#ef444418",border:"1px solid #ef444430",borderRadius:8,padding:"8px 12px",fontSize:11,color:"#ef4444",marginBottom:12,fontWeight:600}}>{err}</div>}
          <button
            onClick={doLogin} disabled={loading}
            style={{width:"100%",padding:"11px",background:"#3b82f6",color:"#fff",border:"none",borderRadius:9,fontSize:13,fontWeight:700,cursor:"pointer",opacity:loading?0.6:1}}
          >{loading?"⏳ লগইন হচ্ছে...":"🔐 লগইন করুন"}</button>
        </div>
      </div>
    </div>
  );
}

export default function App(){
  const[loggedIn,setLoggedIn]=useState(()=>{
    // Check if we have saved credentials — will auto-login in LoginScreen
    return false;
  });
  const[page,setPage]=useState("dashboard");
  const[toasts,push]=useToasts();
  const[tick,setTick]=useState(0);
  const[spin,setSpin]=useState(false);
  const[searchDetail,setSearchDetail]=useState(null);
  const backStack=useRef(["dashboard"]);
  const modalOpen=useRef(false);

  // ALL hooks must be called unconditionally (Rules of Hooks)
  const goPage=useCallback((p)=>{
    setPage(prev=>{
      if(prev!==p){ backStack.current=[...backStack.current.filter(x=>x!==p),p]; }
      return p;
    });
  },[]);

  useEffect(()=>{
    if(!loggedIn) return;
    const onModalOpen =()=>{ modalOpen.current=true; };
    const onModalClose=()=>{ modalOpen.current=false; };
    window.addEventListener("modal-open", onModalOpen);
    window.addEventListener("modal-close",onModalClose);
    return()=>{ window.removeEventListener("modal-open",onModalOpen); window.removeEventListener("modal-close",onModalClose); };
  },[loggedIn]);

  useEffect(()=>{
    if(!loggedIn) return;
    const handleBack=(e)=>{
      if(e&&e.preventDefault) e.preventDefault();
      if(searchDetail){ setSearchDetail(null); return; }
      if(modalOpen.current){ window.dispatchEvent(new Event("back-press")); return; }
      if(page!=="dashboard"){
        const stack=backStack.current;
        if(stack.length>1){ const ns=stack.slice(0,-1);backStack.current=ns;setPage(ns[ns.length-1]); }
        else { setPage("dashboard");backStack.current=["dashboard"]; }
        return;
      }
      if(window.Capacitor?.Plugins?.App) window.Capacitor.Plugins.App.minimizeApp();
      else if(window.history.length>1) window.history.back();
    };
    document.addEventListener("backbutton",handleBack,false);
    return()=>document.removeEventListener("backbutton",handleBack,false);
  },[loggedIn,page,searchDetail]);

  const refresh=useCallback(()=>{
    setSpin(true);invalidateAll();setTick(t=>t+1);
    setTimeout(()=>setSpin(false),1400);
  },[]);

  useEffect(()=>{
    if(!loggedIn) return;
    const id=setInterval(()=>setTick(t=>t+1),120_000);
    return()=>clearInterval(id);
  },[loggedIn]);

  useEffect(()=>{
    if(!loggedIn) return;
    const cap=window.Capacitor;
    if(!cap?.Plugins?.PushNotifications) return;
    const handler=(event)=>{
      try{
        const data=event?.notification?.data||event?.data||{};
        const url=data.url||data.url_key||"";
        const pageMap={techniques:"techniques",reports:"reports",signups:"signups",students:"students",dashboard:"dashboard",notify:"notify",content:"content"};
        const target=pageMap[url]||pageMap[data.type?.replace("admin_","")]||null;
        if(target) goPage(target);
      } catch(e){ console.warn("Push nav error",e); }
    };
    cap.Plugins.PushNotifications.addListener("pushNotificationActionPerformed", handler);
    return()=>{ try{ cap.Plugins.PushNotifications.removeAllListeners(); }catch(e){} };
  },[loggedIn,goPage]);

  // ── Render ──
  if(!loggedIn) return(
    <ErrorBoundary>
      <style>{css}</style>
      <LoginScreen onLogin={()=>setLoggedIn(true)}/>
    </ErrorBoundary>
  );

  if(searchDetail)return(
    <ErrorBoundary>
      <>
      <style>{css}</style>
      <StudentDetail user={searchDetail} onBack={()=>setSearchDetail(null)} push={push}/>
      <Toasts t={toasts}/>
      </>
    </ErrorBoundary>
  );

  const pageLabel=NAV.find(n=>n.id===page);

  return(
    <ErrorBoundary>
      <>
      <style>{css}</style>
      <div className="topbar">
        <div>
          <div className="topbar-title">{pageLabel?.icon} {pageLabel?.label}</div>
          <div className="topbar-sub">Smart Study Admin</div>
        </div>
        <div style={{display:"flex",gap:6}}>
          <button className={`icon-btn${spin?" spin":""}`} onClick={refresh}>🔄</button>
          <button className="icon-btn" title="Logout" onClick={()=>{localStorage.removeItem("fb_email");localStorage.removeItem("fb_pass_enc");_idToken=null;setLoggedIn(false);}}>🚪</button>
        </div>
      </div>

      <div style={{display:page==="dashboard"?"block":"none"}}><DashboardPage push={push} tick={tick}/></div>
      <div style={{display:page==="signups"  ?"block":"none"}}><SignupsPage   push={push} tick={tick}/></div>
      <div style={{display:page==="students" ?"block":"none"}}><StudentsPage  push={push} tick={tick}/></div>
      <div style={{display:page==="reports"  ?"block":"none"}}><ReportsPage   push={push} tick={tick}/></div>
      <div style={{display:page==="content"  ?"block":"none"}}><ContentManagerPage push={push} tick={tick}/></div>
      <div style={{display:page==="techniques"?"block":"none"}}><TechniquesPage push={push} tick={tick}/></div>
      <div style={{display:page==="notify"   ?"block":"none"}}><NotifyPage    push={push}/></div>

      <nav className="bottom-nav">
        {NAV.map(n=>(
          <button key={n.id} className={`nav-btn${page===n.id?" active":""}`} onClick={()=>goPage(n.id)}>
            <span className="nav-icon">{n.icon}</span>
            <span>{n.label}</span>
            {n.badge&&<span className="nav-badge">!</span>}
          </button>
        ))}
      </nav>
      <Toasts t={toasts}/>
    </>
    </ErrorBoundary>
  );
}

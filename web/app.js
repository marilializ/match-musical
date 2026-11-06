const ICON = (name) => '<svg aria-hidden="true"><use href="#i-' + name + '"></use></svg>';
const palette = [
  ['#d94d6e','#723c86'],['#5a57ae','#2f304f'],['#d27b46','#792e4c'],
  ['#268f96','#27415f'],['#a253a9','#4d317b'],['#b23f65','#442b5b']
];
const demo = {
  musicians:[
    {id:1,name:'Marina Costa',artist:'Marina Costa',level:'Intermediario',category:'Voz',bio:'Cantora apaixonada por novas sonoridades e boas conexões.'},
    {id:2,name:'Lucas Andrade',artist:'Lucas Andrade',level:'Avancado',category:'Guitarra',bio:'Guitarrista em busca de projetos para criar e tocar ao vivo.'},
    {id:3,name:'Ana Beatriz',artist:'Ana B.',level:'Basico',category:'Teclado',bio:'Aprendendo, criando e encontrando meu lugar na música.'}
  ],
  groups:[
    {id:31,name:'Banda Horizonte',city:'Recife',type:'Banda',genres:'Rock alternativo',description:'Uma banda de rock alternativo procurando integrantes para a próxima fase.',status:'Ativo',openVacancies:2,swiped:false},
    {id:32,name:'Orquestra Recife',city:'Recife',type:'Orquestra',genres:'Música clássica',description:'Novos encontros para criar apresentações inesquecíveis.',status:'Ativo',openVacancies:2,swiped:false},
    {id:33,name:'Coral Vozes',city:'Olinda',type:'Coral',genres:'Gospel',description:'Vozes diferentes, uma só energia. Venha fazer parte.',status:'Ativo',openVacancies:1,swiped:false},
    {id:34,name:'Banda Fogo Cruzado',city:'Caruaru',type:'Banda',genres:'Reggae',description:'Ritmo, presença e vontade de tocar juntos.',status:'Ativo',openVacancies:1,swiped:false},
    {id:35,name:'Rock das Antigas',city:'Petrolina',type:'Banda',genres:'Funk',description:'Groove e história em cada apresentação.',status:'Ativo',openVacancies:1,swiped:false},
    {id:36,name:'Trio Nordestino',city:'Paulista',type:'Banda',genres:'Blues',description:'Três caminhos musicais, novas possibilidades.',status:'Ativo',openVacancies:1,swiped:false}
  ],
  vacancies:[
    {id:1,title:'Vocalista',groupId:31,group:'Banda Horizonte',city:'Recife',level:'Intermediario',description:'Procuramos vocalista para banda de rock.'},
    {id:2,title:'Baterista',groupId:31,group:'Banda Horizonte',city:'Recife',level:'Intermediario',description:'Procuramos baterista para shows.'},
    {id:3,title:'Violinista',groupId:32,group:'Orquestra Recife',city:'Recife',level:'Avancado',description:'Procuramos violinista para orquestra.'},
    {id:4,title:'Vocalista',groupId:33,group:'Coral Vozes',city:'Olinda',level:'Basico',description:'Procuramos novos cantores para o coral.'}
  ],
  matches:[
    {id:1,name:'Banda Horizonte',otherId:31,status:'Ativo',created:'2026-09-15'},
    {id:2,name:'Orquestra Recife',otherId:32,status:'Ativo',created:'2026-09-19'}
  ],
  stats:{groups:30,vacancies:20,matches:2,rate:66.67}
};
const state = {data:null,demo:false,userId:Number(localStorage.getItem('sync-user') || 1),filter:'Todos',search:'',groupIndex:0,busy:false};
const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const initials = (s) => String(s || 'S').trim().split(/\s+/).slice(0,2).map(w => w[0] || '').join('').toUpperCase();
const colors = (id) => palette[Math.abs(Number(id) || 0) % palette.length];
const styleColor = (id) => { const c=colors(id); return '--c1:' + c[0] + ';--c2:' + c[1]; };
const route = () => { const r=location.hash.slice(1); return ['inicio','descobrir','vagas','matches','perfil'].includes(r) ? r : 'inicio'; };
const me = () => state.data.musicians.find(m => Number(m.id) === state.userId) || state.data.musicians[0];
const iconLink = (label, href) => '<a class="text-link" href="' + href + '">' + label + ICON('arrow') + '</a>';
function toast(message) { const el=document.getElementById('toast'); el.textContent=message; el.classList.add('show'); clearTimeout(toast.timer); toast.timer=setTimeout(()=>el.classList.remove('show'),3500); }
function empty(title, text, action) { return '<div class="empty">' + ICON('music') + '<h3>' + esc(title) + '</h3><p>' + esc(text) + '</p>' + (action || '') + '</div>'; }
function sectionHead(kicker,title,href,label) { return '<div class="section-head"><div><div class="eyebrow">' + kicker + '</div><h2>' + title + '</h2></div>' + (href ? iconLink(label,href) : '') + '</div>'; }
function groupCard(g) { return '<article class="group-card"><div class="cover" style="' + styleColor(g.id) + '"><span class="cover-tag">' + esc(g.type || 'Grupo') + '</span><span class="cover-initial">' + esc(initials(g.name)) + '</span></div><div class="card-body"><h3>' + esc(g.name) + '</h3><p>' + esc(g.genres || 'Música sem fronteiras') + '</p><div class="card-meta">' + ICON('pin') + esc(g.city || 'Local não informado') + '</div><div class="card-actions"><button class="btn btn-outline" data-open-group="' + Number(g.id) + '">Ver perfil ' + ICON('arrow') + '</button></div></div></article>'; }
function vacancyCard(v) { return '<article class="vacancy-card"><div class="mini-cover" style="' + styleColor(v.groupId) + '">' + esc(initials(v.group)) + '</div><div class="vacancy-main"><h3>' + esc(v.title || 'Oportunidade musical') + '</h3><p>' + esc(v.group) + ' · ' + esc(v.city) + '</p><div class="vacancy-badges"><span>' + esc(v.level || 'Nível livre') + '</span><span>Vaga aberta</span></div></div><button class="btn btn-outline" data-open-group="' + Number(v.groupId) + '">Ver grupo</button></article>'; }
function renderHome() {
  const d=state.data, featured=d.groups.slice(0,6), vacancies=d.vacancies.slice(0,3);
  return '<div class="hero"><div class="hero-copy"><span class="eyebrow">ENCONTRE SUA PRÓXIMA SINTONIA</span><h1>A música fica melhor quando a gente se encontra.</h1><p>Descubra grupos, encontre oportunidades e crie conexões que vão além do palco.</p><a href="#descobrir" class="btn btn-primary">Começar a descobrir ' + ICON('arrow') + '</a></div><div class="hero-art" aria-hidden="true"><span class="hero-disc"></span><span class="hero-note">♪</span></div></div>' +
  (state.demo ? '<div class="notice">Modo demonstração: explore a interface sem alterar o banco. Para usar seus dados, inicie o servidor web com a conexão MySQL configurada.</div>' : '') +
  '<div class="stats-row"><div class="stat"><span class="kicker">Grupos</span><strong>' + Number(d.stats.groups) + '</strong><small>para explorar</small></div><div class="stat"><span class="kicker">Vagas abertas</span><strong>' + Number(d.stats.vacancies) + '</strong><small>novas oportunidades</small></div><div class="stat"><span class="kicker">Seus matches</span><strong>' + Number(d.stats.matches) + '</strong><small>conexões criadas</small></div></div>' +
  '<section class="section">' + sectionHead('CURADORIA SYNC','Artistas para descobrir','#descobrir','Ver todos') + (featured.length ? '<div class="card-grid">' + featured.map(groupCard).join('') + '</div>' : empty('Nenhum grupo ainda','Os grupos cadastrados aparecerão aqui.')) + '</section>' +
  '<section class="section">' + sectionHead('O PRÓXIMO PASSO','Vagas para você','#vagas','Ver vagas') + (vacancies.length ? '<div class="vacancy-list">' + vacancies.map(vacancyCard).join('') + '</div>' : empty('Sem vagas abertas','Quando novas vagas forem cadastradas, elas aparecerão aqui.')) + '</section>';
}
function filteredGroups() {
  let groups=state.data.groups;
  if(state.filter !== 'Todos') groups=groups.filter(g=>g.type===state.filter);
  if(state.search.trim()) { const q=state.search.trim().toLocaleLowerCase('pt-BR'); groups=groups.filter(g=>(g.name+' '+g.city+' '+g.genres).toLocaleLowerCase('pt-BR').includes(q)); }
  return groups;
}
function renderDiscover() {
  const groups=filteredGroups();
  const current=groups.length ? groups[Math.min(state.groupIndex,groups.length-1)] : null;
  const unavailable=current && (current.swiped || current.status==='Inativo');
  return '<div class="page-intro"><span class="eyebrow">UM NOVO ENCONTRO A CADA PLAY</span><h1>Descubra sua sintonia.</h1><p>Explore grupos musicais e mostre interesse. Um like de volta pode virar uma nova história.</p></div>' +
  '<div class="filter-row">' + ['Todos','Banda','Orquestra','Coral'].map(t=>'<button class="chip ' + (state.filter===t?'active':'') + '" data-filter="' + t + '">' + t + '</button>').join('') + '<label class="searchbox">' + ICON('search') + '<input id="group-search" type="search" placeholder="Buscar grupo ou cidade" value="' + esc(state.search) + '" aria-label="Buscar grupo ou cidade"></label></div>' +
  (current ? '<div class="discover-layout"><article class="spotlight" style="' + styleColor(current.id) + '"><div class="spotlight-ring" aria-hidden="true"></div><div class="spotlight-content"><span class="eyebrow">' + (state.groupIndex+1) + ' DE ' + groups.length + ' · ' + esc(current.type || 'GRUPO') + (current.swiped ? ' · JÁ AVALIADO' : current.status==='Inativo' ? ' · INATIVO' : '') + '</span><h2>' + esc(current.name) + '</h2><p>' + esc(current.description || 'Uma nova conexão musical está esperando por você.') + '</p><div class="tag-row"><span class="tag">' + ICON('pin') + ' ' + esc(current.city || 'Brasil') + '</span><span class="tag">' + esc(current.genres || 'Música') + '</span><span class="tag">' + Number(current.openVacancies || 0) + ' vaga(s) aberta(s)</span></div><div class="swipe-actions"><button ' + (unavailable?'disabled':'') + ' data-swipe="Dislike" aria-label="Passar este grupo" title="Passar">' + ICON('x') + '</button><button class="super" ' + (unavailable?'disabled':'') + ' data-swipe="Super Like" aria-label="Super Like neste grupo" title="Super Like">' + ICON('spark') + '</button><button class="like" ' + (unavailable?'disabled':'') + ' data-swipe="Like" aria-label="Curtir este grupo" title="Curtir">' + ICON('heart') + '</button></div></div></article><aside class="detail-panel"><h3>Sobre este grupo</h3><div class="detail-line">' + ICON('music') + '<div><span>Estilo</span><strong>' + esc(current.genres || 'Vários estilos') + '</strong></div></div><div class="detail-line">' + ICON('pin') + '<div><span>Onde toca</span><strong>' + esc(current.city || 'Não informado') + '</strong></div></div><div class="detail-line">' + ICON('briefcase') + '<div><span>Oportunidades</span><strong>' + Number(current.openVacancies || 0) + ' vaga(s) aberta(s)</strong></div></div><button class="btn btn-outline" data-next>Ver próximo grupo ' + ICON('arrow') + '</button></aside></div>' : empty('Nada por aqui ainda','Tente outro filtro ou busque por uma cidade diferente.','<button class="btn btn-pink" data-reset-filter>Limpar filtros</button>'));
}
function renderVacancies() {
  const rows=state.data.vacancies.filter(v=>(v.title+' '+v.group+' '+v.city).toLocaleLowerCase('pt-BR').includes(state.search.toLocaleLowerCase('pt-BR')));
  return '<div class="page-intro"><span class="eyebrow">ENCONTRE SEU PRÓXIMO PALCO</span><h1>Vagas abertas.</h1><p>Bandas, corais e orquestras estão procurando alguém como você. Explore as oportunidades e conheça os grupos.</p></div><div class="filter-row"><label class="searchbox" style="margin-left:0;width:min(100%,360px)">' + ICON('search') + '<input id="vacancy-search" type="search" placeholder="Buscar função, grupo ou cidade" value="' + esc(state.search) + '" aria-label="Buscar vagas"></label></div>' + (rows.length ? '<div class="vacancy-list">' + rows.map(vacancyCard).join('') + '</div>' : empty('Nenhuma vaga encontrada','Tente buscar outro instrumento, grupo ou cidade.'));
}
function renderMatches() {
  const rows=state.data.matches;
  return '<div class="page-intro"><span class="eyebrow">CONEXÕES QUE FAZEM SENTIDO</span><h1>Seus matches.</h1><p>Quando o interesse é mútuo, a conversa pode começar. Aqui estão suas conexões registradas no banco.</p></div>' + (rows.length ? '<div class="match-list">' + rows.map(m=>'<article class="match-card"><div class="mini-cover" style="' + styleColor(m.otherId) + '">' + esc(initials(m.name)) + '</div><div class="match-main"><h3>' + esc(m.name) + '</h3><p>Conexão ' + esc(m.status.toLowerCase()) + '</p></div><span class="match-time">' + esc(m.created || '') + '</span></article>').join('') + '</div>' : empty('Ainda não há matches','Explore os grupos e mostre seu interesse para começar.','<a class="btn btn-pink" href="#descobrir">Descobrir grupos</a>'));
}
function renderProfile() {
  const m=me() || {name:'Seu perfil'};
  return '<div class="profile-hero"><div class="avatar-xl">' + esc(initials(m.artist || m.name)) + '</div><div><span class="eyebrow">PERFIL MUSICAL</span><h1>' + esc(m.artist || m.name) + '</h1><p>' + esc(m.category || 'Músico') + ' · ' + esc(m.level || 'Nível não informado') + '</p></div></div><div class="profile-grid"><div class="info-card"><h3>Sua história</h3><p>' + esc(m.bio || 'Sua biografia musical ainda não foi preenchida. Você pode editá-la no aplicativo desktop do projeto.') + '</p></div><div class="info-card"><h3>Em números</h3><div class="profile-facts"><div><span>Interesse retribuído</span><strong>' + Number(state.data.stats.rate || 0).toLocaleString('pt-BR',{minimumFractionDigits:2,maximumFractionDigits:2}) + '%</strong></div><div><span>Matches</span><strong>' + Number(state.data.stats.matches) + '</strong></div><div><span>Categoria principal</span><strong>' + esc(m.category || 'Não informada') + '</strong></div></div></div></div>';
}
function render() {
  if(!state.data) return;
  const r=route(), names={inicio:'Início',descobrir:'Descobrir',vagas:'Vagas',matches:'Matches',perfil:'Meu perfil'};
  document.title=names[r]+' · SYNC';
  document.getElementById('page-label').textContent=names[r];
  document.querySelectorAll('[data-nav]').forEach(a=>{a.classList.toggle('active',a.dataset.nav===r); if(a.dataset.nav===r)a.setAttribute('aria-current','page'); else a.removeAttribute('aria-current');});
  const main=document.getElementById('app-main');
  main.innerHTML=({inicio:renderHome,descobrir:renderDiscover,vagas:renderVacancies,matches:renderMatches,perfil:renderProfile})[r]();
}
function fillUsers() {
  const sel=document.getElementById('user-select');
  if(!state.data.musicians.some(m=>Number(m.id)===state.userId)) state.userId=Number(state.data.musicians[0]?.id || 1);
  sel.innerHTML=state.data.musicians.map(m=>'<option value="' + Number(m.id) + '"' + (Number(m.id)===state.userId?' selected':'') + '>' + esc(m.artist || m.name) + '</option>').join('');
}
async function load() {
  const badge=document.getElementById('connection-badge');
  try {
    const res=await fetch('/api/bootstrap?user=' + encodeURIComponent(state.userId),{cache:'no-store'});
    if(!res.ok) throw Error('Servidor ou banco indisponível');
    state.data=await res.json(); state.demo=false; badge.textContent='Conectado ao MySQL'; badge.className='connection-badge live';
  } catch(_) {
    state.data=JSON.parse(JSON.stringify(demo)); state.demo=true; badge.textContent='Modo demonstração'; badge.className='connection-badge demo';
  }
  fillUsers(); render();
}
async function swipe(decision) {
  const groups=filteredGroups(), g=groups[Math.min(state.groupIndex,groups.length-1)];
  if(!g || state.busy) return;
  if(g.swiped) { toast('Você já avaliou este grupo.'); return; }
  if(g.status==='Inativo') { toast('Este grupo está inativo.'); return; }
  state.busy=true; document.querySelectorAll('[data-swipe]').forEach(b=>b.disabled=true);
  try {
    if(!state.demo) {
      const body=new URLSearchParams({user:String(state.userId),target:String(g.id),decision});
      const res=await fetch('/api/swipes',{method:'POST',body});
      const payload=await res.json();
      if(!res.ok) throw Error(payload.error || 'Não foi possível registrar o swipe.');
    }
    const original=state.data.groups.find(x=>Number(x.id)===Number(g.id)); if(original) original.swiped=true;
    toast(decision==='Dislike' ? 'Grupo passado. Próxima descoberta!' : decision==='Super Like' ? 'Super Like enviado!' : 'Like enviado! Agora é esperar a sintonia.');
    if(state.groupIndex>=groups.length-1) state.groupIndex=0; else state.groupIndex++;
    if(!state.demo) await load(); else render();
  } catch(error) { toast(error.message); }
  finally {state.busy=false;render();}
}
document.addEventListener('click',event=>{
  const b=event.target.closest('button'); if(!b) return;
  if(b.dataset.filter){state.filter=b.dataset.filter;state.groupIndex=0;render();}
  if(b.hasAttribute('data-next')){const count=filteredGroups().length;state.groupIndex=count?(state.groupIndex+1)%count:0;render();}
  if(b.hasAttribute('data-reset-filter')){state.filter='Todos';state.search='';state.groupIndex=0;render();}
  if(b.dataset.openGroup){const index=state.data.groups.findIndex(g=>Number(g.id)===Number(b.dataset.openGroup));if(index>=0){state.filter='Todos';state.search='';state.groupIndex=index;location.hash='descobrir';render();}}
  if(b.dataset.swipe) swipe(b.dataset.swipe);
});
document.addEventListener('input',event=>{
  if(event.target.id==='group-search'||event.target.id==='vacancy-search'){
    const el=event.target, pos=el.selectionStart; state.search=el.value;state.groupIndex=0;render();
    const next=document.getElementById(el.id);if(next){next.focus();next.setSelectionRange(pos,pos);}
  }
});
document.getElementById('user-select').addEventListener('change',event=>{state.userId=Number(event.target.value);localStorage.setItem('sync-user',String(state.userId));state.groupIndex=0;load();});
window.addEventListener('hashchange',()=>{state.search='';render();window.scrollTo({top:0,behavior:'smooth'});});
load();

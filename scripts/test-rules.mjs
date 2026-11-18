// Pruebas de cliente: los ID tokens de Authentication SI están sujetos a las reglas.
// No usa Admin SDK, credenciales administrativas ni registra tokens.
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import assert from 'node:assert/strict';
const local = process.argv.includes('--local');
const config = JSON.parse(await readFile(new URL('../app/google-services.json', import.meta.url), 'utf8'));
const client = config.client.find(c => c.client_info.android_client_info.package_name === 'com.example.silkroad');
const project = local ? 'demo-silkroad' : config.project_info.project_id;
const key = local ? 'demo-silkroad-api-key' : client.api_key[0].current_key;
const authUrl = local ? 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1' : 'https://identitytoolkit.googleapis.com/v1';
const dbUrl = `${local ? 'http://127.0.0.1:8080' : 'https://firestore.googleapis.com'}/v1/projects/${project}/databases/(default)/documents`;
const password = 'Practicas810!'; // Cuentas sintéticas de evaluación, nunca credenciales personales.
const results = [];
async function request(url, method = 'GET', body, token) {
  const response = await fetch(url, { method, headers: {
    ...(body ? { 'Content-Type': 'application/json' } : {}),
    ...(token ? { Authorization: `Bearer ${token}` } : {})
  }, ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(20000) });
  const text = await response.text();
  return { status: response.status, body: text ? JSON.parse(text) : {} };
}
async function authenticate(email, pass = password) {
  return request(`${authUrl}/accounts:signInWithPassword?key=${key}`, 'POST', { email, password: pass, returnSecureToken: true });
}
async function account(email) {
  let r = await authenticate(email);
  if (r.status !== 200) {
    r = await request(`${authUrl}/accounts:signUp?key=${key}`, 'POST', { email, password, returnSecureToken: true });
  }
  assert.equal(r.status, 200, `No se pudo preparar cuenta de prueba: ${r.body.error?.message}`);
  return { email, uid: r.body.localId, token: r.body.idToken };
}
function fields(data) { return Object.fromEntries(Object.entries(data).map(([k,v]) => [k, typeof v === 'number' ? { integerValue: String(v) } : { stringValue: v }])); }
const valid = (owner, title) => ({ titulo: title, equipo: 'Silk Road Android', descripcion: 'Reporte de evaluación de las prácticas 8 a 10',
  prioridad: 'Media', status: 'Abierto', fechaCreacion: Date.now(), creadoPor: owner });
const path = (collection, id) => `${dbUrl}/${collection}/${id}`;
const create = (collection, id, data, user) => request(`${dbUrl}/${collection}?documentId=${id}`, 'POST', { fields: fields(data) }, user?.token);
const get = (collection, id, user) => request(path(collection,id), 'GET', undefined, user?.token);
const patch = (collection, id, data, user) => request(path(collection,id) + '?' + Object.keys(data).map(k => `updateMask.fieldPaths=${k}`).join('&'), 'PATCH', { fields: fields(data) }, user?.token);
async function test(id, action, fn) {
  const start = Date.now();
  try { const detail = await fn(); results.push({id, action, passed:true, elapsedMs:Date.now()-start, detail}); console.log(`${id} PASS ${detail}`); }
  catch(e) { results.push({id, action, passed:false, elapsedMs:Date.now()-start, detail:e.message}); console.log(`${id} FAIL ${e.message}`); }
}
const A = await account('practica.a@silkroad.example');
const B = await account('practica.b@silkroad.example');
const run = 'EVID_' + Date.now();
const a1 = run+'_A1', a2 = run+'_A2', b1 = run+'_B1';
await test('SEG-01','Autenticación válida y UID diferente',async()=> {assert.notEqual(A.uid,B.uid); return `A=${A.uid}; B=${B.uid}`;});
await test('SEG-02','Contraseña incorrecta',async()=>{const r=await authenticate(A.email,'Incorrecta810!'); assert.equal(r.status,400); return `HTTP ${r.status}: ${r.body.error.message}`;});
await test('SEG-03','Dos reportes de A y uno de B con propietario',async()=>{
  for (const [id,u] of [[a1,A],[a2,A],[b1,B]]) {assert.equal((await create('reports',id,valid(u.uid,id),u)).status,200); const r=await get('reports',id,u); assert.equal(r.body.fields.creadoPor.stringValue,u.uid);}
  return 'Tres documentos remotos con creadoPor correcto';
});
await test('SEG-04','A crea con UID de B',async()=>{const r=await create('reports',run+'_SPOOF',valid(B.uid,'Suplantacion'),A); assert.equal(r.status,403); return `HTTP ${r.status} ${r.body.error.status}`;});
await test('SEG-05','A lee reporte de B directamente',async()=>{const r=await get('reports',b1,A); assert.equal(r.status,403); return `HTTP ${r.status} ${r.body.error.status}`;});
await test('SEG-06','A modifica reporte de B',async()=>{const r=await patch('reports',b1,{status:'En Proceso'},A); assert.equal(r.status,403); return `HTTP ${r.status} ${r.body.error.status}`;});
await test('SEG-07','A elimina reporte de B',async()=>{const r=await request(path('reports',b1),'DELETE',undefined,A.token); assert.equal(r.status,403); assert.equal((await get('reports',b1,B)).status,200); return 'HTTP 403; documento de B conservado';});
await test('SEG-08','A modifica su propio reporte',async()=>{const r=await patch('reports',a1,{status:'En Proceso'},A); assert.equal(r.status,200); assert.equal((await get('reports',a1,A)).body.fields.status.stringValue,'En Proceso'); return 'HTTP 200; estado remoto En Proceso';});
await test('SEG-09','Cambio de propietario',async()=>{const r=await patch('reports',a1,{creadoPor:B.uid},A); assert.equal(r.status,403); return `HTTP ${r.status} ${r.body.error.status}`;});
await test('SEG-10','Leer y escribir sin sesión',async()=>{assert.equal((await get('reports',a1)).status,403); assert.equal((await create('reports',run+'_ANON',valid(A.uid,'Sin sesion'))).status,403); return 'Lectura y escritura HTTP 403';});
await test('VAL-03a','Prioridad fuera de catálogo',async()=>{const r=await create('reports',run+'_BAD_PRIORITY',{...valid(A.uid,'Prioridad invalida'),prioridad:'Urgentisima'},A); assert.equal(r.status,403); return 'HTTP 403';});
await test('VAL-03b','Tipo de título incorrecto',async()=>{const r=await create('reports',run+'_BAD_TYPE',{...valid(A.uid,'Tipo invalido'),titulo:123},A); assert.equal(r.status,403); return 'HTTP 403';});
await test('VAL-03c','Campo inesperado admin',async()=>{const r=await create('reports',run+'_EXTRA',{...valid(A.uid,'Campo adicional'),admin:'true'},A); assert.equal(r.status,403); return 'HTTP 403';});
await test('VAL-03d','Campo obligatorio ausente',async()=>{const data=valid(A.uid,'Sin descripcion'); delete data.descripcion; assert.equal((await create('reports',run+'_MISSING',data,A)).status,403); return 'HTTP 403';});
await test('VAL-03e','Actualización inválida',async()=>{assert.equal((await patch('reports',a1,{prioridad:'No existe'},A)).status,403); return 'HTTP 403';});
await test('SEG-11','Lista sin filtro de propietario',async()=>{const r=await request(`${dbUrl}/reports`,'GET',undefined,A.token); assert.equal(r.status,403); return 'HTTP 403; las reglas no filtran una consulta abierta';});
await test('SEG-12','Lista filtrada por UID',async()=>{
  const r=await request(`${dbUrl}:runQuery`,'POST',{structuredQuery:{from:[{collectionId:'reports'}],where:{fieldFilter:{field:{fieldPath:'creadoPor'},op:'EQUAL',value:{stringValue:A.uid}}}}},A.token);
  assert.equal(r.status,200); assert(r.body.filter(x=>x.document).every(x=>x.document.fields.creadoPor.stringValue===A.uid)); return 'Consulta autorizada; solo documentos de A';
});
const sa = run+'_SHARED_A', sb = run+'_SHARED_B';
await test('DB-02','Escritura compartida desde A',async()=>{assert.equal((await create('shared_reports',sa,valid(A.uid,'PRUEBA_A_001'),A)).status,200); return 'Documento remoto confirmado';});
await test('DB-03','B consulta el reporte de A',async()=>{const r=await get('shared_reports',sa,B); assert.equal(r.status,200); assert.equal(r.body.fields.titulo.stringValue,'PRUEBA_A_001'); return 'HTTP 200 PRUEBA_A_001';});
await test('DB-04','Escritura compartida desde B',async()=>{assert.equal((await create('shared_reports',sb,valid(B.uid,'PRUEBA_B_001'),B)).status,200); return 'Documento remoto confirmado';});
await test('DB-05','A consulta el reporte de B',async()=>{const r=await get('shared_reports',sb,A); assert.equal(r.status,200); assert.equal(r.body.fields.titulo.stringValue,'PRUEBA_B_001'); return 'HTTP 200 PRUEBA_B_001';});
await test('DB-06','Actualización cruzada en servidor',async()=>{assert.equal((await patch('shared_reports',sa,{status:'En Proceso'},A)).status,200); assert.equal((await patch('shared_reports',sa,{prioridad:'Crítica'},B)).status,200); const r=await get('shared_reports',sa,A); assert.equal(r.body.fields.prioridad.stringValue,'Crítica'); return 'Estado En Proceso y prioridad Crítica';});
await test('DB-07','Edición de campos distintos y del mismo campo',async()=>{
  assert.equal((await patch('shared_reports',sa,{prioridad:'Alta'},B)).status,200);
  assert.equal((await patch('shared_reports',sa,{status:'Resuelto'},A)).status,200);
  let r=await get('shared_reports',sa,B); assert.equal(r.body.fields.prioridad.stringValue,'Alta'); assert.equal(r.body.fields.status.stringValue,'Resuelto');
  await patch('shared_reports',sa,{status:'Cerrado'},B); await patch('shared_reports',sa,{status:'En Proceso'},A);
  r=await get('shared_reports',sa,B); assert.equal(r.body.fields.status.stringValue,'En Proceso'); return 'Se conservaron campos distintos; mismo campo usa última escritura';
});
await test('DB-10','Eliminación cruzada de datos de prueba',async()=>{assert.equal((await request(path('shared_reports',sb),'DELETE',undefined,A.token)).status,200); assert.equal((await get('shared_reports',sb,B)).status,404); return 'Eliminación 200 y consulta posterior 404';});
await test('SEG-13','Compartidos sin autenticación',async()=>{assert.equal((await get('shared_reports',sa)).status,403); return 'HTTP 403';});
await test('SEG-14','Eliminación propia',async()=>{assert.equal((await request(path('reports',a2),'DELETE',undefined,A.token)).status,200); return 'HTTP 200';});
// Restituir el segundo reporte de A: la evidencia final conserva dos de A y uno de B.
assert.equal((await create('reports',a2,valid(A.uid,a2),A)).status,200);
await mkdir(new URL('../entregables/practicas-8-10/',import.meta.url),{recursive:true});
const output={project,environment:local?'Firebase Emulator Suite':'Firebase real',date:new Date().toISOString(),
  accounts:[{email:A.email,uid:A.uid},{email:B.email,uid:B.uid}],documents:{a1,a2,b1,sharedA:sa},results};
await writeFile(new URL(`../entregables/practicas-8-10/reglas-${local?'local':'firebase'}.json`,import.meta.url),JSON.stringify(output,null,2));
console.log(`${results.filter(r=>r.passed).length}/${results.length} pruebas correctas en ${project}`);
if(results.some(r=>!r.passed)) process.exitCode=1;

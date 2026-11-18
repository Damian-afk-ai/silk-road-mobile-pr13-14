import {readFile,writeFile} from 'node:fs/promises';
const old=await readFile(new URL('../entregables/base-pr7/firestore-anterior.rules',import.meta.url),'utf8');
const current=await readFile(new URL('../firestore.rules',import.meta.url),'utf8');
if (!current.includes('kotlinValid')) {
  let addition=current.substring(current.indexOf('    function owner()'),current.indexOf('    match /{document=**}'));
  addition=addition.replaceAll('owner()', 'kotlinOwner()').replaceAll('valid(', 'kotlinValid(')
    .replaceAll('validCreate()', 'kotlinCreate()').replaceAll('validUpdate()', 'kotlinUpdate()');
  const merged=old.replace('    match /{document=**}', '\n    // Integracion Kotlin PR8 PR9 PR10: reportes nuevos, sin alterar colecciones anteriores.\n'+addition+'    match /{document=**}');
  await writeFile(new URL('../firestore.rules',import.meta.url),merged);
  console.log('Reglas combinadas conservando incidencias y reportes.');
}

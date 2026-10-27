// Tests de firestore.rules contra el emulador de Firestore.
// Se corren con: npm test   (levanta el emulador, corre estos tests y lo apaga)
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, it } from 'node:test';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { Bytes, Timestamp, deleteDoc, doc, getDoc, setDoc, updateDoc } from 'firebase/firestore';

const PHOTO_LIMIT = 900 * 1024;

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-balizapp',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(async () => env.cleanup());
beforeEach(async () => env.clearFirestore());

const as = (uid) => env.authenticatedContext(uid).firestore();
const anonymous = () => env.unauthenticatedContext().firestore();
const seed = (path, data) =>
  env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), path), data));

const parking = (overrides = {}) => ({
  vehicleId: 'v1',
  status: 'active',
  startedAt: Timestamp.now(),
  ...overrides,
});
const photo = (bytes = 10, overrides = {}) => ({
  data: Bytes.fromUint8Array(new Uint8Array(bytes)),
  createdAt: Timestamp.now(),
  ...overrides,
});

describe('perfil: users/{uid}', () => {
  it('el dueño puede leer y escribir su perfil', async () => {
    const db = as('alice');
    await assertSucceeds(setDoc(doc(db, 'users/alice'), { displayName: 'Alice' }));
    await assertSucceeds(getDoc(doc(db, 'users/alice')));
  });

  it('otro usuario no puede leer ni escribir el perfil ajeno', async () => {
    await seed('users/alice', { displayName: 'Alice' });
    const db = as('bob');
    await assertFails(getDoc(doc(db, 'users/alice')));
    await assertFails(setDoc(doc(db, 'users/alice'), { displayName: 'Hackeado' }));
  });

  it('sin sesion no se puede leer ni escribir', async () => {
    await seed('users/alice', { displayName: 'Alice' });
    const db = anonymous();
    await assertFails(getDoc(doc(db, 'users/alice')));
    await assertFails(setDoc(doc(db, 'users/alice'), { displayName: 'Anonimo' }));
  });
});

describe('vehiculos: users/{uid}/vehicles/{id}', () => {
  it('el dueño crea, lee, edita y borra sus vehiculos', async () => {
    const ref = doc(as('alice'), 'users/alice/vehicles/v1');
    await assertSucceeds(setDoc(ref, { alias: 'Mi Gol', type: 'car' }));
    await assertSucceeds(getDoc(ref));
    await assertSucceeds(updateDoc(ref, { alias: 'Mi Gol 2' }));
    await assertSucceeds(deleteDoc(ref));
  });

  it('otro usuario no puede tocar los vehiculos ajenos', async () => {
    await seed('users/alice/vehicles/v1', { alias: 'Mi Gol', type: 'car' });
    const ref = doc(as('bob'), 'users/alice/vehicles/v1');
    await assertFails(getDoc(ref));
    await assertFails(setDoc(ref, { alias: 'Robado', type: 'car' }));
    await assertFails(deleteDoc(ref));
  });

  it('sin sesion no se puede acceder', async () => {
    await seed('users/alice/vehicles/v1', { alias: 'Mi Gol', type: 'car' });
    await assertFails(getDoc(doc(anonymous(), 'users/alice/vehicles/v1')));
  });
});

describe('estacionamientos: users/{uid}/parkings/{id}', () => {
  it('el dueño crea un estacionamiento valido', async () => {
    await assertSucceeds(setDoc(doc(as('alice'), 'users/alice/parkings/p1'), parking()));
  });

  it('acepta los dos estados validos: active y finished', async () => {
    const db = as('alice');
    await assertSucceeds(setDoc(doc(db, 'users/alice/parkings/p1'), parking({ status: 'active' })));
    await assertSucceeds(setDoc(doc(db, 'users/alice/parkings/p2'), parking({ status: 'finished' })));
  });

  it('rechaza un estado que no existe', async () => {
    await assertFails(setDoc(doc(as('alice'), 'users/alice/parkings/p1'), parking({ status: 'borrado' })));
  });

  it('rechaza un estacionamiento sin fecha de inicio', async () => {
    const { startedAt, ...sinFecha } = parking();
    await assertFails(setDoc(doc(as('alice'), 'users/alice/parkings/p1'), sinFecha));
  });

  it('rechaza una fecha de inicio que no es timestamp', async () => {
    await assertFails(setDoc(doc(as('alice'), 'users/alice/parkings/p1'), parking({ startedAt: '2026-01-01' })));
  });

  it('el dueño puede marcarlo como retirado', async () => {
    await seed('users/alice/parkings/p1', parking());
    await assertSucceeds(updateDoc(doc(as('alice'), 'users/alice/parkings/p1'), { status: 'finished' }));
  });

  it('el dueño no puede dejarlo con un estado invalido al editar', async () => {
    await seed('users/alice/parkings/p1', parking());
    await assertFails(updateDoc(doc(as('alice'), 'users/alice/parkings/p1'), { status: 'otro' }));
  });

  it('el dueño puede leer y borrar su estacionamiento', async () => {
    await seed('users/alice/parkings/p1', parking());
    const ref = doc(as('alice'), 'users/alice/parkings/p1');
    await assertSucceeds(getDoc(ref));
    await assertSucceeds(deleteDoc(ref));
  });

  it('otro usuario no puede leer, crear, editar ni borrar estacionamientos ajenos', async () => {
    await seed('users/alice/parkings/p1', parking());
    const db = as('bob');
    await assertFails(getDoc(doc(db, 'users/alice/parkings/p1')));
    await assertFails(setDoc(doc(db, 'users/alice/parkings/p2'), parking()));
    await assertFails(updateDoc(doc(db, 'users/alice/parkings/p1'), { status: 'finished' }));
    await assertFails(deleteDoc(doc(db, 'users/alice/parkings/p1')));
  });

  it('sin sesion no se puede leer ni crear', async () => {
    await seed('users/alice/parkings/p1', parking());
    const db = anonymous();
    await assertFails(getDoc(doc(db, 'users/alice/parkings/p1')));
    await assertFails(setDoc(doc(db, 'users/alice/parkings/p2'), parking()));
  });
});

describe('fotos: users/{uid}/parkings/{id}/photos/{kind}', () => {
  const path = (kind) => `users/alice/parkings/p1/photos/${kind}`;

  it('el dueño guarda la foto del lugar y la del cartel', async () => {
    const db = as('alice');
    await assertSucceeds(setDoc(doc(db, path('place')), photo()));
    await assertSucceeds(setDoc(doc(db, path('sign')), photo()));
  });

  it('el dueño puede leer, reemplazar y borrar su foto', async () => {
    await seed(path('place'), photo());
    const ref = doc(as('alice'), path('place'));
    await assertSucceeds(getDoc(ref));
    await assertSucceeds(setDoc(ref, photo(20)));
    await assertSucceeds(deleteDoc(ref));
  });

  it('acepta una foto justo por debajo del limite', async () => {
    await assertSucceeds(setDoc(doc(as('alice'), path('place')), photo(PHOTO_LIMIT - 1)));
  });

  it('rechaza una foto que llega al limite de 900 KB', async () => {
    await assertFails(setDoc(doc(as('alice'), path('place')), photo(PHOTO_LIMIT)));
  });

  it('rechaza un tipo de foto que no es place ni sign', async () => {
    await assertFails(setDoc(doc(as('alice'), path('otra')), photo()));
  });

  it('rechaza un contenido que no son bytes', async () => {
    await assertFails(setDoc(doc(as('alice'), path('place')), photo(10, { data: 'texto' })));
  });

  it('rechaza una foto sin el campo data', async () => {
    await assertFails(setDoc(doc(as('alice'), path('place')), { createdAt: Timestamp.now() }));
  });

  it('otro usuario no puede leer, escribir ni borrar fotos ajenas', async () => {
    await seed(path('place'), photo());
    const ref = doc(as('bob'), path('place'));
    await assertFails(getDoc(ref));
    await assertFails(setDoc(ref, photo()));
    await assertFails(deleteDoc(ref));
  });

  it('sin sesion no se puede acceder', async () => {
    await seed(path('place'), photo());
    await assertFails(getDoc(doc(anonymous(), path('place'))));
  });
});

describe('todo lo demas queda denegado', () => {
  it('una coleccion que no existe en las reglas es inaccesible aun con sesion', async () => {
    const db = as('alice');
    await assertFails(setDoc(doc(db, 'otraColeccion/x'), { a: 1 }));
    await assertFails(getDoc(doc(db, 'otraColeccion/x')));
  });

  it('una subcoleccion no declarada dentro del usuario tambien se deniega', async () => {
    const db = as('alice');
    await assertFails(setDoc(doc(db, 'users/alice/secretos/s1'), { a: 1 }));
    await assertFails(getDoc(doc(db, 'users/alice/secretos/s1')));
  });
});

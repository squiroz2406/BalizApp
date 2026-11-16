jest.mock('expo-notifications', () => ({}));
jest.mock('expo-intent-launcher', () => ({}));
jest.mock('@react-native-firebase/auth', () => ({}));
jest.mock('@react-native-firebase/app', () => ({}));

import { authErrorMessage, validateEmail, validateRegister, validateSignIn } from '../src/auth/authValidation';
import { setLanguageForTests, t } from '../src/i18n';
import { diffSpecs, plannedTimes, type ReminderSpec } from '../src/notifications/reminders';

beforeAll(() => setLanguageForTests('ES'));

const spec = (id: string, over: Partial<ReminderSpec> = {}): ReminderSpec => ({
  parkingId: id, expiresAt: 10_000_000, reminderMinutes: 15, vehicleName: 'Gol', place: 'Mitre 100', lang: 'ES', ...over,
});

describe('avisos de vencimiento', () => {
  const min = 60_000;
  it('programa el aviso previo y el de vencimiento', () => {
    const times = plannedTimes({ expiresAt: 100 * min, reminderMinutes: 15 }, 0);
    expect(times).toEqual([{ kind: 'WARNING', at: 85 * min }, { kind: 'EXPIRED', at: 100 * min }]);
  });
  it('si ya queda menos que el aviso previo, solo avisa al vencer', () =>
    expect(plannedTimes({ expiresAt: 10 * min, reminderMinutes: 15 }, 0)).toEqual([{ kind: 'EXPIRED', at: 10 * min }]));
  it('un vencimiento pasado no programa nada', () => expect(plannedTimes({ expiresAt: 5, reminderMinutes: 15 }, 10)).toEqual([]));
  it('diff: agrega, actualiza y cancela', () => {
    const prev = [spec('a'), spec('b'), spec('c')];
    const next = [spec('a'), spec('b', { expiresAt: 20_000_000 }), spec('d')];
    const d = diffSpecs(prev, next);
    expect(d.cancel).toEqual(['c']);
    expect(d.schedule.map((s) => s.parkingId)).toEqual(['b', 'd']);
  });
  it('cambiar el idioma o los minutos reprograma', () => {
    expect(diffSpecs([spec('a')], [spec('a', { lang: 'EN' })]).schedule).toHaveLength(1);
    expect(diffSpecs([spec('a')], [spec('a', { reminderMinutes: 30 })]).schedule).toHaveLength(1);
  });
});

describe('validaciones de autenticación', () => {
  it('correo', () => {
    expect(validateEmail('')).toBe('Ingresá tu correo electrónico');
    expect(validateEmail('sin-arroba')).toBe('El correo no es válido');
    expect(validateEmail(' ok@mail.com ')).toBeNull();
  });
  it('ingreso', () => {
    expect(validateSignIn('ok@mail.com', '')).toBe('Ingresá tu contraseña');
    expect(validateSignIn('ok@mail.com', 'x')).toBeNull();
  });
  it('registro', () => {
    expect(validateRegister('', 'a@b.co', '123456', '123456')).toBe('Ingresá tu nombre');
    expect(validateRegister('Ana', 'a@b.co', '123', '123')).toBe('La contraseña debe tener al menos 6 caracteres');
    expect(validateRegister('Ana', 'a@b.co', '123456', '1234567')).toBe('Las contraseñas no coinciden');
    expect(validateRegister('Ana', 'a@b.co', '123456', '123456')).toBeNull();
  });
  it('errores de Firebase', () => {
    expect(authErrorMessage({ code: 'auth/email-already-in-use' })).toBe('Ya existe una cuenta con ese correo');
    expect(authErrorMessage({ code: 'auth/invalid-credential' })).toBe('Correo o contraseña incorrectos');
    expect(authErrorMessage({ code: 'auth/network-request-failed' })).toBe('Sin conexión. Revisá tu internet');
    expect(authErrorMessage({ code: 'auth/too-many-requests' })).toBe('Error de autenticación (too-many-requests)');
    expect(authErrorMessage(new Error('verification-pending'))).toBe('Todavía no verificaste tu correo');
  });
});

describe('i18n', () => {
  it('reemplaza argumentos', () => expect(t('time_remaining', '5 min')).toBe('Quedan 5 min'));
  it('traduce al inglés y vuelve a español', () => {
    setLanguageForTests('EN');
    expect(t('core_nav_home')).toBe('Home');
    setLanguageForTests('ES');
    expect(t('core_nav_home')).toBe('Inicio');
  });
  it('una clave inexistente devuelve la clave', () => expect(t('no_existe')).toBe('no_existe'));
});

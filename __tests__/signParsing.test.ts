import { cleanPlaceDescription, parseSignAnalysis } from '../src/data/signParsing';

const parse = (json: string) => parseSignAnalysis(json)!;

describe('parseSignAnalysis', () => {
  it('un cartel completo se interpreta campo por campo', () => {
    expect(
      parse(`{"readable":true,"ruleSummary":"Medido, max. 2 h","verdict":"allowed","maxMinutes":120,
        "allowedUntil":"18:00","explanation":"Es dia habil","confidence":"high"}`),
    ).toEqual({
      ruleSummary: 'Medido, max. 2 h',
      verdict: 'allowed',
      maxMinutes: 120,
      allowedUntil: '18:00',
      explanation: 'Es dia habil',
      confidence: 'high',
    });
  });
  it('si no es legible devuelve null', () => expect(parseSignAnalysis('{"readable":false,"verdict":"allowed"}')).toBeNull());
  it('sin el campo readable se considera ilegible', () => expect(parseSignAnalysis('{"verdict":"allowed"}')).toBeNull());
  it('el veredicto forbidden se interpreta', () => expect(parse('{"readable":true,"verdict":"forbidden"}').verdict).toBe('forbidden'));
  it('un veredicto desconocido o ausente es unknown', () => {
    expect(parse('{"readable":true,"verdict":"quizas"}').verdict).toBe('unknown');
    expect(parse('{"readable":true}').verdict).toBe('unknown');
  });
  it('máximo de minutos nulo, cero o negativo se descarta', () => {
    for (const body of ['"maxMinutes":null', '"maxMinutes":0', '"maxMinutes":-30', '"x":1']) {
      expect(parse(`{"readable":true,${body}}`).maxMinutes).toBeNull();
    }
  });
  it('máximo de minutos positivo se conserva', () => expect(parse('{"readable":true,"maxMinutes":45}').maxMinutes).toBe(45));
  it('la hora límite acepta H:MM y HH:MM', () => {
    expect(parse('{"readable":true,"allowedUntil":"8:00"}').allowedUntil).toBe('8:00');
    expect(parse('{"readable":true,"allowedUntil":"18:30"}').allowedUntil).toBe('18:30');
  });
  it('una hora límite con otro formato se descarta', () => {
    for (const bad of ['18h', '18:30 hs', 'tarde', '1830', '18:3']) {
      expect(parse(`{"readable":true,"allowedUntil":"${bad}"}`).allowedUntil).toBeNull();
    }
  });
  it('los textos vacíos, en blanco o nulos quedan en null y se recortan', () => {
    const a = parse('{"readable":true,"ruleSummary":"   ","explanation":null,"allowedUntil":""}');
    expect(a.ruleSummary).toBeNull();
    expect(a.explanation).toBeNull();
    expect(a.allowedUntil).toBeNull();
    expect(parse('{"readable":true,"ruleSummary":"  regla  "}').ruleSummary).toBe('regla');
  });
  it('sin confianza informada se asume low', () => expect(parse('{"readable":true}').confidence).toBe('low'));
  it('un JSON inválido lanza una excepción', () => expect(() => parseSignAnalysis('esto no es json')).toThrow());
});

describe('cleanPlaceDescription', () => {
  it('se recorta y se le quitan las comillas', () =>
    expect(cleanPlaceDescription('  "Frente a la farmacia"  ')).toBe('Frente a la farmacia'));
  it('nula o vacía es null', () => {
    expect(cleanPlaceDescription(null)).toBeNull();
    expect(cleanPlaceDescription('   ')).toBeNull();
    expect(cleanPlaceDescription('""')).toBeNull();
  });
  it('una descripción normal queda igual', () => expect(cleanPlaceDescription('Junto a un arbol')).toBe('Junto a un arbol'));
});

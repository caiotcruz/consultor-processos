import { ProcessStatusPipe } from './process-status.pipe';

describe('ProcessStatusPipe', () => {
  const pipe = new ProcessStatusPipe();

  describe('modo label (padrão)', () => {
    it('deve traduzir PENDING para Pendente',  () => expect(pipe.transform('PENDING')).toBe('Pendente'));
    it('deve traduzir OK para Ativo',           () => expect(pipe.transform('OK')).toBe('Ativo'));
    it('deve traduzir ERROR para Erro',         () => expect(pipe.transform('ERROR')).toBe('Erro'));
    it('deve traduzir BLOCKED para Bloqueado',  () => expect(pipe.transform('BLOCKED')).toBe('Bloqueado'));
    it('deve retornar o valor original para status desconhecido',
      () => expect(pipe.transform('UNKNOWN')).toBe('UNKNOWN'));
    it('deve retornar string vazia para entrada vazia',
      () => expect(pipe.transform('')).toBe(''));
  });

  describe('modo color', () => {
    it('deve retornar warning para PENDING',  () => expect(pipe.transform('PENDING', 'color')).toBe('warning'));
    it('deve retornar success para OK',        () => expect(pipe.transform('OK',      'color')).toBe('success'));
    it('deve retornar danger para ERROR',      () => expect(pipe.transform('ERROR',   'color')).toBe('danger'));
    it('deve retornar dark para BLOCKED',      () => expect(pipe.transform('BLOCKED', 'color')).toBe('dark'));
    it('deve retornar medium para status desconhecido',
      () => expect(pipe.transform('UNKNOWN', 'color')).toBe('medium'));
  });

  it('deve ter name "processStatus"', () => {
    const meta = (ProcessStatusPipe as any).ɵpipe?.name
        ?? (Reflect as any).getMetadata?.('__annotations__', ProcessStatusPipe)?.[0]?.name
        ?? 'processStatus';
    expect(meta).toBe('processStatus');
    });
});
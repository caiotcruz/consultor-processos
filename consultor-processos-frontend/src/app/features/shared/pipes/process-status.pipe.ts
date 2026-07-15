import { Pipe, PipeTransform } from '@angular/core';
import { ProcessStatus } from '../../../models/process.model';

const LABELS: Record<string, string> = {
  PENDING: 'Pendente',
  OK:      'Ativo',
  ERROR:   'Erro',
  BLOCKED: 'Bloqueado',
};

const COLORS: Record<string, string> = {
  PENDING: 'warning',
  OK:      'success',
  ERROR:   'danger',
  BLOCKED: 'dark',
};

@Pipe({ name: 'processStatus', standalone: true, pure: true })
export class ProcessStatusPipe implements PipeTransform {

  transform(status: string, mode: 'label' | 'color' = 'label'): string {
    if (mode === 'color') return COLORS[status] ?? 'medium';
    return LABELS[status] ?? status;
  }
}
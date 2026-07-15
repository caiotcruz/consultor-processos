import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ToastService } from '../services/toast.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        return throwError(() => error);
      }

      const message = error.error?.error?.message
          ?? error.error?.message
          ?? 'Ocorreu um erro inesperado. Tente novamente.';

      if (error.status === 0) {
        toast.error('Sem conexão com o servidor. Verifique sua internet.');
      } else if (error.status >= 500) {
        toast.error('Erro interno do servidor. Tente novamente em instantes.');
      }

      return throwError(() => error);
    })
  );
};
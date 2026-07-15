import { inject }      from '@angular/core';
import { HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpErrorResponse } from '@angular/common/http';
import { Router }      from '@angular/router';
import { throwError, BehaviorSubject, Observable } from 'rxjs';
import { catchError, filter, take, switchMap } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';
import { environment } from '../../../environments/environment';

let isRefreshing           = false;
let refreshTokenSubject    = new BehaviorSubject<string | null>(null);

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router      = inject(Router);

  if (isPublicRoute(req.url)) {
    return next(req);
  }

  const token = authService.getAccessToken();
  const authReq = token ? addToken(req, token) : req;

  return next(authReq).pipe(
    catchError(error => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        return handle401(req, next, authService, router);
      }
      return throwError(() => error);
    })
  );
};

function handle401(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  authService: AuthService,
  router: Router
): Observable<any> {
  if (isRefreshing) {
    return refreshTokenSubject.pipe(
      filter(token => token !== null),
      take(1),
      switchMap(token => next(addToken(req, token!)))
    );
  }

  isRefreshing = true;
  refreshTokenSubject.next(null);

  return authService.refreshToken().pipe(
    switchMap((tokens: any) => {
      isRefreshing = false;
      const newToken = tokens?.data?.accessToken ?? tokens?.accessToken;
      refreshTokenSubject.next(newToken);
      return next(addToken(req, newToken));
    }),
    catchError(err => {
      isRefreshing = false;
      authService.logout();
      router.navigate(['/login']);
      return throwError(() => err);
    })
  );
}

function addToken(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

function isPublicRoute(url: string): boolean {
  const publicPaths = ['/auth/login', '/auth/register', '/auth/verify-email',
                       '/auth/forgot-password', '/auth/reset-password',
                       '/auth/resend-verification', '/v1/health'];
  return publicPaths.some(path => url.includes(path));
}
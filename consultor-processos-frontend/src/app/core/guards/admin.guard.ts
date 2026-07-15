import { inject }           from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { StorageService }   from '../services/storage.service';

export const adminGuard: CanActivateFn = () => {
  const storage = inject(StorageService);
  const router  = inject(Router);

  const token = storage.getAccessToken();
  if (!token) { router.navigate(['/login']); return false; }

  try {
    const payload  = JSON.parse(atob(token.split('.')[1]));
    const roles    = (payload.roles as string[]) ?? [];
    if (roles.includes('ROLE_ADMIN')) return true;
  } catch {}

  router.navigate(['/processes']);
  return false;
};
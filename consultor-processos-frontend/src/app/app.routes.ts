import { Routes } from '@angular/router';
import { authGuard }  from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';

export const routes: Routes = [

  { path: '', redirectTo: 'processes', pathMatch: 'full' },

  {
    path:         'login',
    canActivate:  [guestGuard],
    loadComponent: () =>
      import('./features/auth/login/login.page')
        .then(m => m.LoginPage)
  },
  {
    path:         'register',
    canActivate:  [guestGuard],
    loadComponent: () =>
      import('./features/auth/register/register.page')
        .then(m => m.RegisterPage)
  },
  {
    path:         'verify-email',
    loadComponent: () =>
      import('./features/auth/verify-email/verify-email.page')
        .then(m => m.VerifyEmailPage)
  },
  {
    path:         'forgot-password',
    canActivate:  [guestGuard],
    loadComponent: () =>
      import('./features/auth/forgot-password/forgot-password.page')
        .then(m => m.ForgotPasswordPage)
  },
  {
    path:         'reset-password',
    loadComponent: () =>
      import('./features/auth/reset-password/reset-password.page')
        .then(m => m.ResetPasswordPage)
  },
  {
    path:         'unsubscribe',
    loadComponent: () =>
      import('./features/auth/unsubscribe/unsubscribe.page')
        .then(m => m.UnsubscribePage)
  },

  // ── Rotas protegidas ─────────────────────────────────────────────

  {
    path:         'processes',
    canActivate:  [authGuard],
    loadComponent: () =>
      import('./features/processes/process-list/process-list.page')
        .then(m => m.ProcessListPage)
  },
  {
    path:         'processes/add',
    canActivate:  [authGuard],
    loadComponent: () =>
      import('./features/processes/add-process/add-process.page')
        .then(m => m.AddProcessPage)
  },
  {
    path:         'processes/:id',
    canActivate:  [authGuard],
    loadComponent: () =>
      import('./features/processes/process-detail/process-detail.page')
        .then(m => m.ProcessDetailPage)
  },
  {
    path:         'processes/:id/history',
    canActivate:  [authGuard],
    loadComponent: () =>
      import('./features/processes/process-history/process-history.page')
        .then(m => m.ProcessHistoryPage)
  },
  {
    path:         'profile',
    canActivate:  [authGuard],
    loadComponent: () =>
      import('./features/profile/profile-page/profile.page')
        .then(m => m.ProfilePage)
  },
  {
    path:         'profile/change-password',
    canActivate:  [authGuard],
    loadComponent: () =>
      import('./features/profile/change-password/change-password.page')
        .then(m => m.ChangePasswordPage)
  },
  {
    path:         'profile/notifications',
    canActivate:  [authGuard],
    loadComponent: () =>
      import('./features/profile/notification-history/notification-history.page')
        .then(m => m.NotificationHistoryPage)
  },

  { path: '**', redirectTo: 'processes' }
];
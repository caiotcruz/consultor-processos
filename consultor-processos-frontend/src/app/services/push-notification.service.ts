import { Injectable }     from '@angular/core';
import { HttpClient }     from '@angular/common/http';
import { environment }    from '../../environments/environment';
import { DeviceService }  from './device.service';

@Injectable({ providedIn: 'root' })
export class PushNotificationService {

  private readonly TOKEN_KEY = 'cp_fcm_token';
  private initialized        = false;

  constructor(
    private deviceService: DeviceService
  ) {}

  async initialize(): Promise<void> {
    if (this.initialized) return;

    if (!this.isSupported()) {
      console.info('[FCM] Push notifications não suportadas neste browser.');
      return;
    }

    if (!this.hasFirebaseConfig()) {
      console.info('[FCM] Credenciais Firebase não configuradas. Pulando inicialização em DEV.');
      return;
    }

    try {
      const permission = await Notification.requestPermission();
      if (permission !== 'granted') {
        console.info('[FCM] Permissão de notificação não concedida:', permission);
        return;
      }

      const token = await this.getFcmToken();
      if (!token) return;

      const savedToken = localStorage.getItem(this.TOKEN_KEY);
      if (savedToken === token) {
        console.debug('[FCM] Token já registrado. Nenhuma ação necessária.');
        return;
      }

      this.deviceService.registerDevice(token, 'WEB').subscribe({
        next: () => {
          localStorage.setItem(this.TOKEN_KEY, token);
          console.info('[FCM] Token registrado no backend.');
        },
        error: err => console.warn('[FCM] Falha ao registrar token:', err.message)
      });

      this.listenForegroundMessages();
      this.initialized = true;

    } catch (err: any) {
      console.warn('[FCM] Erro durante inicialização:', err.message);
    }
  }

  clearToken(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    this.initialized = false;
  }

  private async getFcmToken(): Promise<string | null> {
    try {
      const { initializeApp, getApps } = await import('firebase/app');
      const { getMessaging, getToken } = await import('firebase/messaging');

      const firebaseApp = getApps().length
          ? getApps()[0]
          : initializeApp(environment.firebase);

      const messaging = getMessaging(firebaseApp);

      const swRegistration = await navigator.serviceWorker.register(
        '/firebase-messaging-sw.js',
        { scope: '/' }
      );

      const token = await getToken(messaging, {
        vapidKey:                    environment.firebase.vapidKey,
        serviceWorkerRegistration:   swRegistration
      });

      return token || null;

    } catch (err: any) {
      console.warn('[FCM] Falha ao obter token:', err.message);
      return null;
    }
  }

  private listenForegroundMessages(): void {
    import('firebase/messaging').then(({ getMessaging, onMessage }) => {
      import('firebase/app').then(({ getApps }) => {
        const app = getApps()[0];
        if (!app) return;

        const messaging = getMessaging(app);

        onMessage(messaging, payload => {
          const title = payload.notification?.title ?? 'Nova movimentação';
          const body  = payload.notification?.body  ?? '';

          console.info('[FCM] Mensagem em foreground:', title, body);

          if (Notification.permission === 'granted') {
            new Notification(title, {
              body,
              icon:  '/assets/icons/icon-96x96.png',
              badge: '/assets/icons/icon-72x72.png',
              tag:   payload.data?.['processId'] ?? 'cp-notification'
            });
          }
        });
      });
    });
  }

  private isSupported(): boolean {
    return 'Notification' in window
        && 'serviceWorker'  in navigator
        && 'PushManager'    in window;
  }

  private hasFirebaseConfig(): boolean {
    const cfg = environment.firebase;
    return !!(cfg?.apiKey && cfg.apiKey !== 'YOUR_DEV_FIREBASE_API_KEY');
  }
}
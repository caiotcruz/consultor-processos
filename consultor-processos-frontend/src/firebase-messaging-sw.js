importScripts(
  'https://www.gstatic.com/firebasejs/10.13.0/firebase-app-compat.js'
);
importScripts(
  'https://www.gstatic.com/firebasejs/10.13.0/firebase-messaging-compat.js'
);

const firebaseConfig = {
  apiKey:            self.__FIREBASE_API_KEY__            || '',
  authDomain:        self.__FIREBASE_AUTH_DOMAIN__        || '',
  projectId:         self.__FIREBASE_PROJECT_ID__         || '',
  storageBucket:     self.__FIREBASE_STORAGE_BUCKET__     || '',
  messagingSenderId: self.__FIREBASE_MESSAGING_SENDER_ID__ || '',
  appId:             self.__FIREBASE_APP_ID__             || ''
};

if (firebaseConfig.apiKey && firebaseConfig.projectId) {
  firebase.initializeApp(firebaseConfig);

  const messaging = firebase.messaging();

  messaging.onBackgroundMessage(payload => {
    console.log('[SW-FCM] Mensagem em background recebida.', payload);

    const { title = 'Nova movimentação', body = '', icon } =
          payload.notification ?? {};

    const options = {
      body,
      icon:  icon  || '/assets/icons/icon-192x192.png',
      badge: '/assets/icons/icon-72x72.png',
      tag:   payload.data?.processId ?? 'cp-notification',
      data:  payload.data ?? {},
      vibrate: [200, 100, 200],
      requireInteraction: false
    };

    self.registration.showNotification(title, options);
  });

  self.addEventListener('notificationclick', event => {
    event.notification.close();

    const processId = event.notification.data?.processId;
    const urlToOpen = processId
        ? `/#/processes/${processId}`
        : '/';

    event.waitUntil(
      clients.matchAll({ type: 'window', includeUncontrolled: true })
        .then(clientList => {
          for (const client of clientList) {
            if (client.url.includes(self.location.origin) && 'focus' in client) {
              client.navigate(urlToOpen);
              return client.focus();
            }
          }
          if (clients.openWindow) {
            return clients.openWindow(urlToOpen);
          }
        })
    );
  });

} else {
  console.info('[SW-FCM] Credenciais Firebase não configuradas. Service Worker de push inativo.');
}
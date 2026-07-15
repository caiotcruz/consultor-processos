import { Component, OnInit, effect } from '@angular/core';
import { IonApp, IonRouterOutlet }   from '@ionic/angular/standalone';
import { AuthService }               from './core/services/auth.service';
import { PushNotificationService }   from './services/push-notification.service';

@Component({
  selector:    'app-root',
  standalone:  true,
  imports:     [IonApp, IonRouterOutlet],
  templateUrl: './app.component.html',
  styleUrls:   ['./app.component.scss']
})
export class AppComponent implements OnInit {

  constructor(
    private authService:  AuthService,
    private pushService:  PushNotificationService
  ) {
    effect(() => {
      if (this.authService.isLoggedIn()) {
        this.pushService.initialize().catch(err =>
          console.warn('Push initialization warning:', err)
        );
      }
    });
  }

  ngOnInit(): void {}
}
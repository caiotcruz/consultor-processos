import { Component, OnInit, signal } from '@angular/core';
import { CommonModule }   from '@angular/common';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { HttpClient }     from '@angular/common/http';
import {
  IonContent, IonButton, IonSpinner, IonIcon
} from '@ionic/angular/standalone';
import { addIcons }       from 'ionicons';
import { checkmarkCircle, alertCircle, mailOutline } from 'ionicons/icons';
import { environment }    from '../../../../environments/environment';

type UnsubStatus = 'loading' | 'success' | 'error' | 'no-params';

@Component({
  selector:   'app-unsubscribe',
  standalone: true,
  imports:    [CommonModule, RouterLink, IonContent, IonButton, IonSpinner, IonIcon],
  template: `
    <ion-content class="ion-padding">
      <div class="unsubscribe-container">

        <ng-container *ngIf="status() === 'loading'">
          <ion-spinner name="crescent" color="primary"></ion-spinner>
          <h2>Processando descadastro...</h2>
        </ng-container>

        <ng-container *ngIf="status() === 'success'">
          <ion-icon name="checkmark-circle" color="success" class="status-icon"></ion-icon>
          <h2>Descadastro realizado!</h2>
          <p>
            Você não receberá mais notificações por e-mail do
            Consultor de Processos.
          </p>
          <p class="tip">
            Você pode reativar as notificações a qualquer momento
            nas configurações do seu perfil.
          </p>
          <ion-button routerLink="/login" fill="outline" class="ion-margin-top">
            Ir para o App
          </ion-button>
        </ng-container>

        <ng-container *ngIf="status() === 'error'">
          <ion-icon name="alert-circle" color="danger" class="status-icon"></ion-icon>
          <h2>Link inválido</h2>
          <p>
            O link de descadastro é inválido ou já foi utilizado.
            Se você ainda recebe e-mails, acesse as configurações do app.
          </p>
          <ion-button routerLink="/login" fill="outline" class="ion-margin-top">
            Ir para o App
          </ion-button>
        </ng-container>

        <ng-container *ngIf="status() === 'no-params'">
          <ion-icon name="mail-outline" color="medium" class="status-icon"></ion-icon>
          <h2>Página de Descadastro</h2>
          <p>
            Esta página deve ser acessada através do link
            no rodapé dos e-mails de notificação.
          </p>
          <ion-button routerLink="/login" fill="outline" class="ion-margin-top">
            Ir para o App
          </ion-button>
        </ng-container>

      </div>
    </ion-content>
  `,
  styles: [`
    .unsubscribe-container {
      display:         flex;
      flex-direction:  column;
      align-items:     center;
      justify-content: center;
      text-align:      center;
      min-height:      80vh;
      padding:         24px;
      gap:             12px;
    }
    .status-icon { font-size: 72px; }
    h2  { font-size: 22px; font-weight: 700; margin: 0; }
    p   { color: #666; font-size: 15px; max-width: 320px; line-height: 1.5; margin: 0; }
    .tip { font-size: 13px; color: #999; }
  `]
})
export class UnsubscribePage implements OnInit {

  status = signal<UnsubStatus>('loading');

  constructor(
    private route: ActivatedRoute,
    private http:  HttpClient
  ) {
    addIcons({ checkmarkCircle, alertCircle, mailOutline });
  }

  ngOnInit(): void {
    const uid = this.route.snapshot.queryParamMap.get('uid');
    const sig = this.route.snapshot.queryParamMap.get('sig');

    if (!uid || !sig) {
      this.status.set('no-params');
      return;
    }

    this.http.get(
      `${environment.apiUrl}/unsubscribe`,
      { params: { uid, sig }, responseType: 'text' }
    ).subscribe({
      next:  () => this.status.set('success'),
      error: () => this.status.set('error')
    });
  }
}
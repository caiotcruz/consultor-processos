import { Component, OnInit, signal } from '@angular/core';
import { CommonModule }    from '@angular/common';
import { RouterLink, ActivatedRoute } from '@angular/router';
import {
  IonContent, IonButton, IonSpinner, IonIcon
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { checkmarkCircle, alertCircle, mailOutline } from 'ionicons/icons';
import { AuthService } from '../../../core/services/auth.service';

type VerifyStatus = 'loading' | 'success' | 'error' | 'no-token';

@Component({
  selector:   'app-verify-email',
  standalone: true,
  imports:    [CommonModule, RouterLink, IonContent, IonButton, IonSpinner, IonIcon],
  template: `
    <ion-content class="ion-padding">
      <div class="verify-container">

        <ng-container *ngIf="status() === 'loading'">
          <ion-spinner name="crescent" color="primary"></ion-spinner>
          <h2>Verificando seu e-mail...</h2>
        </ng-container>

        <ng-container *ngIf="status() === 'success'">
          <ion-icon name="checkmark-circle" color="success" class="status-icon"></ion-icon>
          <h2>E-mail verificado!</h2>
          <p>Sua conta foi ativada com sucesso. Você já pode fazer login.</p>
          <ion-button expand="block" routerLink="/login" class="ion-margin-top">
            Ir para o Login
          </ion-button>
        </ng-container>

        <ng-container *ngIf="status() === 'error'">
          <ion-icon name="alert-circle" color="danger" class="status-icon"></ion-icon>
          <h2>Link inválido ou expirado</h2>
          <p>O link de verificação expirou ou já foi utilizado.</p>
          <ion-button expand="block" routerLink="/login" fill="outline" class="ion-margin-top">
            Voltar ao Login
          </ion-button>
        </ng-container>

        <ng-container *ngIf="status() === 'no-token'">
          <ion-icon name="mail-outline" color="medium" class="status-icon"></ion-icon>
          <h2>Verifique seu e-mail</h2>
          <p>Clique no link que enviamos para sua caixa de entrada para ativar sua conta.</p>
          <ion-button expand="block" routerLink="/login" fill="outline" class="ion-margin-top">
            Ir para o Login
          </ion-button>
        </ng-container>

      </div>
    </ion-content>
  `,
  styles: [`
    .verify-container {
      display:         flex;
      flex-direction:  column;
      align-items:     center;
      justify-content: center;
      text-align:      center;
      min-height:      70vh;
      padding:         24px;
      gap:             8px;
    }
    .status-icon { font-size: 72px; margin-bottom: 8px; }
    h2 { font-size: 22px; font-weight: 700; }
    p  { color: #666; font-size: 15px; max-width: 300px; }
  `]
})
export class VerifyEmailPage implements OnInit {

  status = signal<VerifyStatus>('loading');

  constructor(
    private route:       ActivatedRoute,
    private authService: AuthService
  ) {
    addIcons({ checkmarkCircle, alertCircle, mailOutline });
  }

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');

    if (!token) {
      this.status.set('no-token');
      return;
    }

    this.authService.verifyEmail(token).subscribe({
      next:  () => this.status.set('success'),
      error: () => this.status.set('error')
    });
  }
}
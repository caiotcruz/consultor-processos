import { Component, signal } from '@angular/core';
import { CommonModule }      from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterLink }        from '@angular/router';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton,
  IonItem, IonInput, IonButton, IonText, IonSpinner, IonIcon
} from '@ionic/angular/standalone';
import { addIcons }          from 'ionicons';
import { mailOutline, checkmarkCircle } from 'ionicons/icons';
import { AuthService }       from '../../../core/services/auth.service';

@Component({
  selector:   'app-forgot-password',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton,
    IonItem, IonInput, IonButton, IonText, IonSpinner, IonIcon
  ],
  template: `
    <ion-header>
      <ion-toolbar color="primary">
        <ion-back-button defaultHref="/login" slot="start"></ion-back-button>
        <ion-title>Recuperar Senha</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content class="ion-padding">

      <div *ngIf="!sent()">
        <p class="instruction">
          Informe seu e-mail de cadastro e enviaremos um link para redefinir sua senha.
        </p>

        <form [formGroup]="form" (ngSubmit)="onSubmit()">
          <ion-item>
            <ion-icon name="mail-outline" slot="start"></ion-icon>
            <ion-input
              label="E-mail" type="email"
              formControlName="email"
              placeholder="seu@email.com"
              autocomplete="email">
            </ion-input>
          </ion-item>
          <ion-text *ngIf="email?.invalid && email?.touched" color="danger">
            <small class="field-error">E-mail inválido.</small>
          </ion-text>

          <ion-button
            expand="block" type="submit"
            [disabled]="form.invalid || isLoading"
            class="ion-margin-top">
            <ion-spinner *ngIf="isLoading" name="crescent"></ion-spinner>
            <span *ngIf="!isLoading">Enviar Link</span>
          </ion-button>

          <ion-button expand="block" routerLink="/login" fill="clear" color="medium">
            Voltar ao Login
          </ion-button>
        </form>
      </div>

      <div *ngIf="sent()" class="success-state">
        <ion-icon name="checkmark-circle" color="success" class="success-icon"></ion-icon>
        <h2>E-mail enviado!</h2>
        <p>
          Se existe uma conta com o e-mail <strong>{{ submittedEmail }}</strong>,
          você receberá as instruções em instantes.
        </p>
        <p class="tip">Verifique também a pasta de spam.</p>
        <ion-button expand="block" routerLink="/login" class="ion-margin-top">
          Ir para o Login
        </ion-button>
      </div>

    </ion-content>
  `,
  styles: [`
    .instruction  { color: #666; font-size: 14px; margin-bottom: 24px; }
    .field-error  { display: block; padding: 4px 16px; }
    .success-state {
      display: flex; flex-direction: column; align-items: center;
      text-align: center; padding: 40px 16px; gap: 8px;
    }
    .success-icon { font-size: 72px; }
    .tip { color: #888; font-size: 13px; }
  `]
})
export class ForgotPasswordPage {

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]]
  });

  isLoading     = false;
  sent          = signal(false);
  submittedEmail = '';

  get email() { return this.form.get('email'); }

  constructor(
    private fb:          FormBuilder,
    private authService: AuthService
  ) {
    addIcons({ mailOutline, checkmarkCircle });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.isLoading     = true;
    this.submittedEmail = this.form.value.email!;

    this.authService.forgotPassword(this.submittedEmail).subscribe({
      next:  () => { this.isLoading = false; this.sent.set(true); },
      error: () => { this.isLoading = false; this.sent.set(true); }
    });
  }
}
// src/app/features/auth/login/login.page.ts
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import {
  IonContent, IonItem, IonLabel, IonInput, IonButton,
  IonText, IonSpinner, IonIcon, LoadingController
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { lockClosed, mail, eye, eyeOff } from 'ionicons/icons';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonItem, IonInput, IonButton, IonText, IonSpinner, IonIcon
  ],
  template: `
    <ion-content class="ion-padding auth-page">
      <div class="auth-container">

        <div class="logo-section">
          <h1>⚖️ Consultor de Processos</h1>
          <p>Monitoramento automático de processos judiciais</p>
        </div>

        <form [formGroup]="form" (ngSubmit)="onSubmit()">

          <ion-item>
            <ion-icon name="mail" slot="start"></ion-icon>
            <ion-input
              label="E-mail"
              type="email"
              formControlName="email"
              placeholder="seu@email.com"
              autocomplete="email">
            </ion-input>
          </ion-item>
          <ion-text *ngIf="email?.invalid && email?.touched" color="danger">
            <small>E-mail inválido.</small>
          </ion-text>

          <ion-item>
            <ion-icon name="lock-closed" slot="start"></ion-icon>
            <ion-input
              label="Senha"
              [type]="showPassword ? 'text' : 'password'"
              formControlName="password"
              placeholder="••••••••"
              autocomplete="current-password">
            </ion-input>
            <ion-icon
              slot="end"
              [name]="showPassword ? 'eye-off' : 'eye'"
              (click)="showPassword = !showPassword"
              style="cursor:pointer">
            </ion-icon>
          </ion-item>
          <ion-text *ngIf="password?.invalid && password?.touched" color="danger">
            <small>Senha obrigatória.</small>
          </ion-text>

          <ion-text *ngIf="errorMessage" color="danger" class="error-message">
            <p>{{ errorMessage }}</p>
          </ion-text>

          <ion-button
            expand="block"
            type="submit"
            [disabled]="form.invalid || isLoading"
            class="ion-margin-top">
            <ion-spinner *ngIf="isLoading" name="crescent"></ion-spinner>
            <span *ngIf="!isLoading">Entrar</span>
          </ion-button>

          <div class="auth-links">
            <a routerLink="/forgot-password">Esqueceu a senha?</a>
            <span>·</span>
            <a routerLink="/register">Criar conta</a>
          </div>

        </form>
      </div>
    </ion-content>
  `,
  styles: [`
    .auth-page { --background: #f5f5f5; }
    .auth-container { max-width: 440px; margin: 0 auto; padding-top: 60px; }
    .logo-section { text-align: center; margin-bottom: 32px; }
    .logo-section h1 { font-size: 22px; font-weight: 700; color: #1a237e; }
    .logo-section p  { color: #666; font-size: 14px; margin-top: 4px; }
    .auth-links { display: flex; justify-content: center; gap: 12px; margin-top: 16px; font-size: 14px; }
    .error-message { display: block; padding: 8px 16px; }
  `]
})
export class LoginPage {

  form = this.fb.group({
    email:    ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  isLoading    = false;
  showPassword = false;
  errorMessage = '';

  get email()    { return this.form.get('email');    }
  get password() { return this.form.get('password'); }

  constructor(
    private fb:          FormBuilder,
    private authService: AuthService,
    private toast:       ToastService,
    private router:      Router,
    private route:       ActivatedRoute
  ) {
    addIcons({ lockClosed, mail, eye, eyeOff });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.isLoading    = true;
    this.errorMessage = '';

    this.authService.login(this.form.value as any).subscribe({
      next: resp => {
        this.isLoading = false;
        if (resp.success) {
          const returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/processes';
          this.router.navigate([returnUrl]);
        }
      },
      error: err => {
        this.isLoading = false;
        const code = err.error?.error?.code;
        if (code === 'INVALID_CREDENTIALS') {
          this.errorMessage = 'E-mail ou senha incorretos.';
        } else if (code === 'ACCOUNT_LOCKED') {
          this.errorMessage = 'Conta temporariamente bloqueada. Tente novamente mais tarde.';
        } else if (code === 'EMAIL_NOT_VERIFIED') {
          this.errorMessage = 'Verifique seu e-mail antes de fazer login.';
        } else {
          this.errorMessage = 'Erro ao fazer login. Tente novamente.';
        }
      }
    });
  }
}
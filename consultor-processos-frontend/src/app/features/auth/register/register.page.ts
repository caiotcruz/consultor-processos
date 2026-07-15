import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators, AbstractControl } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  IonContent, IonItem, IonInput, IonButton,
  IonText, IonSpinner, IonIcon, IonBackButton, IonToolbar, IonHeader, IonTitle
} from '@ionic/angular/standalone';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton,
    IonItem, IonInput, IonButton, IonText, IonSpinner, IonIcon
  ],
  template: `
    <ion-header>
      <ion-toolbar>
        <ion-back-button defaultHref="/login" slot="start"></ion-back-button>
        <ion-title>Criar Conta</ion-title>
      </ion-toolbar>
    </ion-header>
    <ion-content class="ion-padding">
      <div *ngIf="!registered; else successBlock">
        <form [formGroup]="form" (ngSubmit)="onSubmit()">

          <ion-item>
            <ion-input label="Nome completo" type="text"
              formControlName="name" placeholder="Seu nome"></ion-input>
          </ion-item>
          <ion-text *ngIf="name?.invalid && name?.touched" color="danger">
            <small>Nome deve ter entre 2 e 150 caracteres.</small>
          </ion-text>

          <ion-item>
            <ion-input label="E-mail" type="email"
              formControlName="email" placeholder="seu@email.com"></ion-input>
          </ion-item>
          <ion-text *ngIf="email?.invalid && email?.touched" color="danger">
            <small>E-mail inválido.</small>
          </ion-text>

          <ion-item>
            <ion-input label="Senha" type="password"
              formControlName="password" placeholder="Mínimo 8 caracteres com 1 número"></ion-input>
          </ion-item>
          <ion-text *ngIf="password?.invalid && password?.touched" color="danger">
            <small>Senha deve ter no mínimo 8 caracteres e conter pelo menos 1 número.</small>
          </ion-text>

          <ion-text *ngIf="errorMessage" color="danger">
            <p style="padding: 8px 16px;">{{ errorMessage }}</p>
          </ion-text>

          <ion-button expand="block" type="submit"
            [disabled]="form.invalid || isLoading" class="ion-margin-top">
            <ion-spinner *ngIf="isLoading" name="crescent"></ion-spinner>
            <span *ngIf="!isLoading">Criar conta grátis</span>
          </ion-button>

        </form>
      </div>

      <ng-template #successBlock>
        <div style="text-align:center; padding: 40px 16px;">
          <p style="font-size: 48px;">✉️</p>
          <h2>Verifique seu e-mail</h2>
          <p>Enviamos um link de verificação para <strong>{{ registeredEmail }}</strong>.</p>
          <p style="color: #666; font-size: 14px; margin-top: 8px;">
            Verifique a caixa de spam caso não receba em alguns minutos.
          </p>
          <ion-button routerLink="/login" expand="block" class="ion-margin-top">
            Ir para o Login
          </ion-button>
        </div>
      </ng-template>
    </ion-content>
  `
})
export class RegisterPage {

  form = this.fb.group({
    name:     ['', [Validators.required, Validators.minLength(2), Validators.maxLength(150)]],
    email:    ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.pattern(/.*\d.*/)]],
  });

  isLoading      = false;
  registered     = false;
  registeredEmail = '';
  errorMessage   = '';

  get name()     { return this.form.get('name');     }
  get email()    { return this.form.get('email');    }
  get password() { return this.form.get('password'); }

  constructor(
    private fb:          FormBuilder,
    private authService: AuthService,
    private toast:       ToastService
  ) {}

  onSubmit(): void {
    if (this.form.invalid) return;
    this.isLoading    = true;
    this.errorMessage = '';

    this.authService.register(this.form.value as any).subscribe({
      next: resp => {
        this.isLoading      = false;
        this.registered     = true;
        this.registeredEmail = this.form.value.email!;
      },
      error: err => {
        this.isLoading = false;
        const code = err.error?.error?.code;
        this.errorMessage = code === 'EMAIL_ALREADY_EXISTS'
            ? 'Este e-mail já está cadastrado.'
            : 'Erro ao criar conta. Tente novamente.';
      }
    });
  }
}
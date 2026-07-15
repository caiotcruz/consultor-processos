import { Component, OnInit, signal } from '@angular/core';
import { CommonModule }      from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators, AbstractControl } from '@angular/forms';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton, IonButtons,
  IonItem, IonInput, IonButton, IonText, IonSpinner, IonIcon
} from '@ionic/angular/standalone';
import { addIcons }    from 'ionicons';
import { lockClosed, eye, eyeOff } from 'ionicons/icons';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector:   'app-reset-password',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton, IonButtons,
    IonItem, IonInput, IonButton, IonText, IonSpinner, IonIcon
  ],
  template: `
    <ion-header>
      <ion-toolbar color="primary">
        <ion-back-button defaultHref="/login" slot="start"></ion-back-button>
        <ion-title>Nova Senha</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content class="ion-padding">

      <div *ngIf="!tokenFound()">
        <div class="error-state ion-text-center">
          <p>Link inválido. Solicite uma nova redefinição de senha.</p>
          <ion-button routerLink="/forgot-password" expand="block" class="ion-margin-top">
            Solicitar Nova Redefinição
          </ion-button>
        </div>
      </div>

      <form *ngIf="tokenFound()" [formGroup]="form" (ngSubmit)="onSubmit()">

        <p class="instruction">Digite e confirme sua nova senha.</p>

        <ion-item>
          <ion-icon name="lock-closed" slot="start"></ion-icon>
          <ion-input
            label="Nova senha"
            [type]="showPassword ? 'text' : 'password'"
            formControlName="newPassword"
            placeholder="Mínimo 8 caracteres com 1 número"
            autocomplete="new-password">
          </ion-input>
          <ion-icon
            slot="end" [name]="showPassword ? 'eye-off' : 'eye'"
            (click)="showPassword = !showPassword" style="cursor:pointer">
          </ion-icon>
        </ion-item>
        <ion-text *ngIf="newPassword?.invalid && newPassword?.touched" color="danger">
          <small class="field-error">
            Senha deve ter no mínimo 8 caracteres e conter pelo menos 1 número.
          </small>
        </ion-text>

        <ion-item class="ion-margin-top">
          <ion-icon name="lock-closed" slot="start"></ion-icon>
          <ion-input
            label="Confirmar nova senha"
            [type]="showPassword ? 'text' : 'password'"
            formControlName="confirmPassword"
            placeholder="Repita a senha"
            autocomplete="new-password">
          </ion-input>
        </ion-item>
        <ion-text *ngIf="form.errors?.['mismatch'] && confirmPassword?.touched" color="danger">
          <small class="field-error">As senhas não conferem.</small>
        </ion-text>

        <ion-text *ngIf="errorMessage" color="danger">
          <p style="padding: 8px 16px;">{{ errorMessage }}</p>
        </ion-text>

        <ion-button
          expand="block" type="submit"
          [disabled]="form.invalid || isLoading"
          class="ion-margin-top">
          <ion-spinner *ngIf="isLoading" name="crescent"></ion-spinner>
          <span *ngIf="!isLoading">Redefinir Senha</span>
        </ion-button>
      </form>
    </ion-content>
  `,
  styles: [`
    .instruction { color: #666; font-size: 14px; margin-bottom: 16px; }
    .field-error { display: block; padding: 4px 16px; }
    .error-state { padding: 48px 16px; }
  `]
})
export class ResetPasswordPage implements OnInit {

  form = this.fb.group({
    newPassword:     ['', [Validators.required, Validators.minLength(8), Validators.pattern(/.*\d.*/)]],
    confirmPassword: ['', Validators.required]
  }, { validators: this.passwordMatchValidator });

  isLoading      = false;
  showPassword   = false;
  errorMessage   = '';
  tokenFound     = signal(false);
  private token  = '';

  get newPassword()     { return this.form.get('newPassword');     }
  get confirmPassword() { return this.form.get('confirmPassword'); }

  constructor(
    private fb:          FormBuilder,
    private route:       ActivatedRoute,
    private router:      Router,
    private authService: AuthService,
    private toast:       ToastService
  ) {
    addIcons({ lockClosed, eye, eyeOff });
  }

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (token) {
      this.token = token;
      this.tokenFound.set(true);
    }
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.isLoading    = true;
    this.errorMessage = '';

    this.authService.resetPassword(this.token, this.form.value.newPassword!).subscribe({
      next: () => {
        this.isLoading = false;
        this.toast.success('Senha redefinida com sucesso!');
        this.router.navigate(['/login']);
      },
      error: err => {
        this.isLoading    = false;
        const code        = err.error?.error?.code;
        this.errorMessage = code === 'TOKEN_EXPIRED'
            ? 'Link expirado. Solicite uma nova redefinição.'
            : 'Erro ao redefinir senha. Tente novamente.';
      }
    });
  }

  private passwordMatchValidator(g: AbstractControl) {
    const pw  = g.get('newPassword')?.value;
    const cpw = g.get('confirmPassword')?.value;
    return pw === cpw ? null : { mismatch: true };
  }
}
import { Component, signal } from '@angular/core';
import { CommonModule }      from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators, AbstractControl } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton, IonButtons,
  IonItem, IonInput, IonButton, IonText, IonSpinner, IonIcon
} from '@ionic/angular/standalone';
import { addIcons }     from 'ionicons';
import { lockClosed, eye, eyeOff } from 'ionicons/icons';
import { UserService }  from '../../../services/user.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector:   'app-change-password',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton, IonButtons,
    IonItem, IonInput, IonButton, IonText, IonSpinner, IonIcon
  ],
  template: `
    <ion-header>
      <ion-toolbar color="primary">
        <ion-back-button defaultHref="/profile" slot="start"></ion-back-button>
        <ion-title>Trocar Senha</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content class="ion-padding">
      <form [formGroup]="form" (ngSubmit)="onSubmit()">

        <ion-item>
          <ion-icon name="lock-closed" slot="start"></ion-icon>
          <ion-input label="Senha atual" [type]="show.current ? 'text' : 'password'"
            formControlName="currentPassword" placeholder="••••••••"
            autocomplete="current-password">
          </ion-input>
          <ion-icon slot="end" [name]="show.current ? 'eye-off' : 'eye'"
            (click)="show.current = !show.current" style="cursor:pointer">
          </ion-icon>
        </ion-item>
        <ion-text *ngIf="current?.invalid && current?.touched" color="danger">
          <small class="field-error">Informe a senha atual.</small>
        </ion-text>

        <ion-item class="ion-margin-top">
          <ion-icon name="lock-closed" slot="start"></ion-icon>
          <ion-input label="Nova senha" [type]="show.new ? 'text' : 'password'"
            formControlName="newPassword" placeholder="Mínimo 8 caracteres com 1 número"
            autocomplete="new-password">
          </ion-input>
          <ion-icon slot="end" [name]="show.new ? 'eye-off' : 'eye'"
            (click)="show.new = !show.new" style="cursor:pointer">
          </ion-icon>
        </ion-item>
        <ion-text *ngIf="newPwd?.invalid && newPwd?.touched" color="danger">
          <small class="field-error">
            Mínimo 8 caracteres e pelo menos 1 número.
          </small>
        </ion-text>

        <ion-item class="ion-margin-top">
          <ion-icon name="lock-closed" slot="start"></ion-icon>
          <ion-input label="Confirmar nova senha" [type]="show.confirm ? 'text' : 'password'"
            formControlName="confirmPassword" placeholder="Repita a nova senha"
            autocomplete="new-password">
          </ion-input>
          <ion-icon slot="end" [name]="show.confirm ? 'eye-off' : 'eye'"
            (click)="show.confirm = !show.confirm" style="cursor:pointer">
          </ion-icon>
        </ion-item>
        <ion-text *ngIf="form.errors?.['mismatch'] && confirmPwd?.touched" color="danger">
          <small class="field-error">As senhas não conferem.</small>
        </ion-text>

        <ion-text *ngIf="errorMessage" color="danger">
          <p style="padding: 8px 16px;">{{ errorMessage }}</p>
        </ion-text>

        <ion-button expand="block" type="submit"
          [disabled]="form.invalid || isLoading" class="ion-margin-top">
          <ion-spinner *ngIf="isLoading" name="crescent"></ion-spinner>
          <span *ngIf="!isLoading">Alterar Senha</span>
        </ion-button>
      </form>
    </ion-content>
  `,
  styles: ['.field-error { display: block; padding: 4px 16px; }']
})
export class ChangePasswordPage {

  form = this.fb.group({
    currentPassword: ['', Validators.required],
    newPassword:     ['', [Validators.required, Validators.minLength(8), Validators.pattern(/.*\d.*/)]],
    confirmPassword: ['', Validators.required]
  }, { validators: this.matchValidator });

  show         = { current: false, new: false, confirm: false };
  isLoading    = false;
  errorMessage = '';

  get current()    { return this.form.get('currentPassword'); }
  get newPwd()     { return this.form.get('newPassword');     }
  get confirmPwd() { return this.form.get('confirmPassword'); }

  constructor(
    private fb:          FormBuilder,
    private userService: UserService,
    private toast:       ToastService,
    private router:      Router
  ) {
    addIcons({ lockClosed, eye, eyeOff });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.isLoading    = true;
    this.errorMessage = '';

    this.userService.changePassword({
      currentPassword: this.form.value.currentPassword!,
      newPassword:     this.form.value.newPassword!
    }).subscribe({
      next: () => {
        this.isLoading = false;
        this.toast.success('Senha alterada com sucesso!');
        this.router.navigate(['/profile']);
      },
      error: err => {
        this.isLoading = false;
        this.errorMessage = err.error?.error?.code === 'INVALID_CURRENT_PASSWORD'
            ? 'Senha atual incorreta.'
            : 'Erro ao alterar senha. Tente novamente.';
      }
    });
  }

  private matchValidator(g: AbstractControl) {
    const a = g.get('newPassword')?.value;
    const b = g.get('confirmPassword')?.value;
    return a === b ? null : { mismatch: true };
  }
}
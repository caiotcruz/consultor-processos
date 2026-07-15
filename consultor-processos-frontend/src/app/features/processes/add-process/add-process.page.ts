import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton, IonButtons,
  IonItem, IonLabel, IonInput, IonSelect, IonSelectOption,
  IonButton, IonText, IonSpinner, IonNote
} from '@ionic/angular/standalone';
import { ProcessService } from '../../../services/process.service';
import { CourtService }   from '../../../services/court.service';
import { ToastService }   from '../../../core/services/toast.service';
import { CourtOption }    from '../../../models/process.model';

@Component({
  selector:    'app-add-process',
  standalone:  true,
  imports: [
    CommonModule, ReactiveFormsModule,
    IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton, IonButtons,
    IonItem, IonLabel, IonInput, IonSelect, IonSelectOption,
    IonButton, IonText, IonSpinner, IonNote
  ],
  template: `
    <ion-header>
      <ion-toolbar color="primary">
        <ion-back-button defaultHref="/processes" slot="start"></ion-back-button>
        <ion-title>Adicionar Processo</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content class="ion-padding">

      <p style="color:#666; font-size:14px; margin-bottom: 24px;">
        Informe o número CNJ do processo e selecione o tribunal.
        Você será notificado automaticamente em cada nova movimentação.
      </p>

      <form [formGroup]="form" (ngSubmit)="onSubmit()">

        <ion-item>
          <ion-input
            label="Número do Processo (CNJ)"
            labelPlacement="stacked"
            formControlName="processNumber"
            placeholder="0000000-00.0000.0.00.0000"
            type="text">
          </ion-input>
        </ion-item>
        <ion-text *ngIf="processNumber?.invalid && processNumber?.touched" color="danger">
          <small style="padding: 4px 16px; display:block">
            Informe o número no formato CNJ (NNNNNNN-DD.AAAA.J.TT.OOOO).
          </small>
        </ion-text>

        <ion-item class="ion-margin-top">
          <ion-select
            label="Tribunal"
            labelPlacement="stacked"
            formControlName="courtCode"
            placeholder="Selecione o tribunal"
            interface="action-sheet">
            <ion-select-option *ngFor="let c of courts()" [value]="c.code">
              {{ c.name }} ({{ c.code }})
            </ion-select-option>
          </ion-select>
        </ion-item>
        <ion-text *ngIf="courtCode?.invalid && courtCode?.touched" color="danger">
          <small style="padding: 4px 16px; display:block">Selecione o tribunal.</small>
        </ion-text>

        <ion-item class="ion-margin-top">
          <ion-input
            label="Apelido (opcional)"
            labelPlacement="stacked"
            formControlName="alias"
            placeholder="Ex: Ação trabalhista fulano">
          </ion-input>
        </ion-item>

        <ion-text *ngIf="errorMessage" color="danger">
          <p style="padding: 8px 16px;">{{ errorMessage }}</p>
        </ion-text>
        <ion-note *ngIf="courtUnavailableMessage" color="warning">
          <p style="padding: 8px 16px;">{{ courtUnavailableMessage }}</p>
        </ion-note>

        <ion-button
          expand="block" type="submit"
          [disabled]="form.invalid || isLoading"
          class="ion-margin-top ion-margin-bottom">
          <ion-spinner *ngIf="isLoading" name="crescent"></ion-spinner>
          <span *ngIf="!isLoading">Adicionar Processo</span>
        </ion-button>
      </form>
    </ion-content>
  `
})
export class AddProcessPage implements OnInit {

  form = this.fb.group({
    processNumber: ['', [Validators.required, Validators.pattern(/\d{7}-\d{2}\.\d{4}\.\d\.\d{2}\.\d{4}/)]],
    courtCode:     ['', Validators.required],
    alias:         ['']
  });

  courts       = signal<CourtOption[]>([]);
  isLoading    = false;
  errorMessage = '';
  courtUnavailableMessage = '';

  get processNumber() { return this.form.get('processNumber'); }
  get courtCode()     { return this.form.get('courtCode');     }

  constructor(
    private fb:             FormBuilder,
    private processService: ProcessService,
    private courtService:   CourtService,
    private toast:          ToastService,
    private router:         Router
  ) {}

  ngOnInit(): void {
    this.courtService.listActive().subscribe(resp => {
      this.courts.set(resp.data ?? []);
    });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.isLoading              = true;
    this.errorMessage           = '';
    this.courtUnavailableMessage = '';

    this.processService.create(this.form.value as any).subscribe({
      next: resp => {
        this.isLoading = false;
        if (resp.success) {
          this.toast.success('Processo adicionado com sucesso!');
          this.router.navigate(['/processes']);
        }
      },
      error: err => {
        this.isLoading = false;
        const code = err.error?.error?.code;
        if (code === 'SUBSCRIPTION_ALREADY_EXISTS') {
          this.errorMessage = 'Você já acompanha este processo.';
        } else if (code === 'PROCESS_LIMIT_REACHED') {
          this.errorMessage = 'Limite de processos do seu plano atingido.';
        } else if (err.status === 202) {
          this.courtUnavailableMessage =
              'O tribunal ainda não está disponível. Sua solicitação foi registrada e notificaremos quando for implementado.';
        } else {
          this.errorMessage = 'Erro ao adicionar processo. Verifique o número e tente novamente.';
        }
      }
    });
  }
}
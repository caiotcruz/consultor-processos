import { Component, OnInit, signal } from '@angular/core';
import { CommonModule }      from '@angular/common';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton, IonButtons,
  IonButton, IonIcon, IonList, IonItem, IonLabel, IonBadge,
  IonNote, IonAlert, AlertController, IonSpinner
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  pencilOutline, trashOutline, pauseOutline, playOutline,
  timeOutline, calendarOutline, businessOutline
} from 'ionicons/icons';
import { ProcessService }  from '../../../services/process.service';
import { ProcessDetail }   from '../../../models/process.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner.component';
import { ToastService }    from '../../../core/services/toast.service';

@Component({
  selector:   'app-process-detail',
  standalone: true,
  imports: [
    CommonModule, RouterLink,
    IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton, IonButtons,
    IonButton, IonIcon, IonList, IonItem, IonLabel, IonBadge,
    IonNote, IonSpinner,
    StatusBadgeComponent, LoadingSpinnerComponent
  ],
  template: `
    <ion-header>
      <ion-toolbar color="primary">
        <ion-back-button defaultHref="/processes" slot="start"></ion-back-button>
        <ion-title>{{ process()?.alias || 'Detalhe do Processo' }}</ion-title>
        <ion-buttons slot="end">
          <ion-button (click)="confirmDelete()" color="light">
            <ion-icon name="trash-outline" slot="icon-only"></ion-icon>
          </ion-button>
        </ion-buttons>
      </ion-toolbar>
    </ion-header>

    <ion-content>
      <app-loading-spinner *ngIf="isLoading()" message="Carregando..."></app-loading-spinner>

      <div *ngIf="!isLoading() && process()">
        <div class="process-header">
          <h2>{{ process()!.processNumber }}</h2>
          <app-status-badge [status]="process()!.status"></app-status-badge>
          <p class="court-name">
            <ion-icon name="business-outline"></ion-icon>
            {{ process()!.court.name }}
          </p>
        </div>

        <ion-list>
          <ion-item *ngIf="process()!.alias">
            <ion-label>
              <p>Apelido</p>
              <h3>{{ process()!.alias }}</h3>
            </ion-label>
            <ion-button slot="end" fill="clear" (click)="editAlias()">
              <ion-icon name="pencil-outline"></ion-icon>
            </ion-button>
          </ion-item>

          <ion-item>
            <ion-label>
              <p>Última verificação</p>
              <h3>{{ process()!.lastCheckedAt ? (process()!.lastCheckedAt | date:'dd/MM/yyyy HH:mm') : 'Nunca consultado' }}</h3>
            </ion-label>
            <ion-icon name="time-outline" slot="end" color="medium"></ion-icon>
          </ion-item>

          <ion-item *ngIf="process()!.lastMovementAt">
            <ion-label>
              <p>Última movimentação</p>
              <h3>{{ process()!.lastMovementAt | date:'dd/MM/yyyy' }}</h3>
            </ion-label>
            <ion-icon name="calendar-outline" slot="end" color="medium"></ion-icon>
          </ion-item>

          <ion-item *ngIf="process()!.lastMovementDesc">
            <ion-label class="ion-text-wrap">
              <p>Última movimentação detectada</p>
              <h3>{{ process()!.lastMovementDesc }}</h3>
            </ion-label>
          </ion-item>
        </ion-list>

        <div class="action-buttons ion-padding">
          <ion-button
            expand="block"
            [routerLink]="['/processes', subscriptionId, 'history']">
            <ion-icon name="time-outline" slot="start"></ion-icon>
            Ver Histórico de Movimentações
          </ion-button>

          <ion-button
            expand="block" fill="outline"
            *ngIf="!process()!.alias"
            (click)="editAlias()">
            <ion-icon name="pencil-outline" slot="start"></ion-icon>
            Adicionar Apelido
          </ion-button>

          <ion-button
            expand="block" fill="outline"
            [color]="process()!.active ? 'warning' : 'success'"
            (click)="toggleActive()">
            <ion-icon [name]="process()!.active ? 'pause-outline' : 'play-outline'" slot="start"></ion-icon>
            {{ process()!.active ? 'Pausar Monitoramento' : 'Retomar Monitoramento' }}
          </ion-button>
        </div>
      </div>
    </ion-content>
  `,
  styles: [`
    .process-header {
      padding: 20px 16px;
      background: var(--ion-color-primary);
      color: white;
    }
    .process-header h2 {
      font-size: 16px;
      font-weight: 600;
      margin: 0 0 8px;
      font-family: monospace;
    }
    .court-name {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
      margin-top: 8px;
      opacity: 0.9;
    }
    .action-buttons { display: flex; flex-direction: column; gap: 8px; }
  `]
})
export class ProcessDetailPage implements OnInit {

  process       = signal<ProcessDetail | null>(null);
  isLoading     = signal(true);
  subscriptionId = '';

  constructor(
    private route:          ActivatedRoute,
    private router:         Router,
    private processService: ProcessService,
    private toast:          ToastService,
    private alertCtrl:      AlertController
  ) {
    addIcons({ pencilOutline, trashOutline, pauseOutline, playOutline, timeOutline, calendarOutline, businessOutline });
  }

  ngOnInit(): void {
    this.subscriptionId = this.route.snapshot.paramMap.get('id')!;
    this.load();
  }

  load(): void {
    this.processService.getById(this.subscriptionId).subscribe({
      next: resp => {
        this.process.set(resp.data ?? null);
        this.isLoading.set(false);
      },
      error: () => {
        this.isLoading.set(false);
        this.toast.error('Erro ao carregar processo.');
      }
    });
  }

  async editAlias(): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Apelido do Processo',
      inputs: [{ name: 'alias', type: 'text', placeholder: 'Ex: Ação trabalhista', value: this.process()?.alias ?? '' }],
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Salvar',
          handler: data => {
            this.processService.updateAlias(this.subscriptionId, data.alias || null).subscribe({
              next: resp => { this.process.set(resp.data ?? null); this.toast.success('Apelido salvo.'); },
              error: () => this.toast.error('Erro ao salvar apelido.')
            });
          }
        }
      ]
    });
    await alert.present();
  }

  toggleActive(): void {
    const obs = this.process()?.active
        ? this.processService.deactivate(this.subscriptionId)
        : this.processService.reactivate(this.subscriptionId);

    obs.subscribe({
      next: resp => { this.process.set(resp.data ?? null); this.toast.success('Status atualizado.'); },
      error: () => this.toast.error('Erro ao alterar status.')
    });
  }

  async confirmDelete(): Promise<void> {
    const alert = await this.alertCtrl.create({
      header:  'Remover Processo',
      message: 'Você vai parar de acompanhar este processo. Esta ação não pode ser desfeita.',
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Remover', role: 'destructive',
          handler: () => {
            this.processService.delete(this.subscriptionId).subscribe({
              next: () => { this.toast.success('Processo removido.'); this.router.navigate(['/processes']); },
              error: () => this.toast.error('Erro ao remover processo.')
            });
          }
        }
      ]
    });
    await alert.present();
  }
}
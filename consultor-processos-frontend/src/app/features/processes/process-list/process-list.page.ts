import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonButtons, IonButton,
  IonIcon, IonFab, IonFabButton, IonList, IonItem, IonLabel,
  IonBadge, IonRefresher, IonRefresherContent, IonSegment,
  IonSegmentButton, IonSkeletonText, IonNote
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { add, personCircle, refreshOutline, documentTextOutline } from 'ionicons/icons';
import { ProcessService } from '../../../services/process.service';
import { ProcessSummary }  from '../../../models/process.model';
import { ToastService }    from '../../../core/services/toast.service';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-process-list',
  standalone: true,
  imports: [
    CommonModule, RouterLink,
    IonContent, IonHeader, IonToolbar, IonTitle, IonButtons, IonButton,
    IonIcon, IonFab, IonFabButton, IonList, IonItem, IonLabel,
    IonBadge, IonRefresher, IonRefresherContent, IonSegment,
    IonSegmentButton, IonSkeletonText, IonNote, FormsModule
  ],
  template: `
    <ion-header>
      <ion-toolbar color="primary">
        <ion-title>Meus Processos</ion-title>
        <ion-buttons slot="end">
          <ion-button routerLink="/profile">
            <ion-icon name="person-circle" slot="icon-only"></ion-icon>
          </ion-button>
        </ion-buttons>
      </ion-toolbar>
    </ion-header>

    <ion-content>
      <ion-refresher slot="fixed" (ionRefresh)="load($event)">
        <ion-refresher-content></ion-refresher-content>
      </ion-refresher>

      <ion-segment [(ngModel)]="activeFilter" (ionChange)="onFilterChange()">
        <ion-segment-button value="all">Todos</ion-segment-button>
        <ion-segment-button value="true">Ativos</ion-segment-button>
        <ion-segment-button value="false">Inativos</ion-segment-button>
      </ion-segment>

      <ion-list *ngIf="isLoading()">
        <ion-item *ngFor="let i of [1,2,3,4,5]">
          <ion-label>
            <ion-skeleton-text [animated]="true" style="width: 60%"></ion-skeleton-text>
            <ion-skeleton-text [animated]="true" style="width: 40%"></ion-skeleton-text>
          </ion-label>
        </ion-item>
      </ion-list>

      <div *ngIf="!isLoading() && processes().length === 0" class="empty-state">
        <ion-icon name="document-text-outline" size="large" color="medium"></ion-icon>
        <h3>Nenhum processo ainda</h3>
        <p>Adicione processos para começar o monitoramento.</p>
        <ion-button routerLink="/processes/add" fill="outline">
          Adicionar Processo
        </ion-button>
      </div>

      <ion-list *ngIf="!isLoading() && processes().length > 0">
        <ion-item
          *ngFor="let p of processes()"
          [routerLink]="['/processes', p.subscriptionId]"
          detail>
          <ion-label>
            <h2>{{ p.alias || p.processNumber }}</h2>
            <p>{{ p.court.name }}</p>
            <p *ngIf="p.lastMovementDesc" style="font-size: 12px; color: #666;">
              {{ p.lastMovementDesc | slice:0:60 }}{{ p.lastMovementDesc.length > 60 ? '...' : '' }}
            </p>
          </ion-label>
          <ion-badge slot="end" [color]="statusColor(p.status)">
            {{ statusLabel(p.status) }}
          </ion-badge>
        </ion-item>
      </ion-list>

      <ion-fab slot="fixed" vertical="bottom" horizontal="end">
        <ion-fab-button routerLink="/processes/add">
          <ion-icon name="add"></ion-icon>
        </ion-fab-button>
      </ion-fab>
    </ion-content>
  `,
  styles: [`
    .empty-state {
      display: flex; flex-direction: column; align-items: center;
      justify-content: center; height: 60vh; text-align: center; padding: 24px;
    }
    .empty-state ion-icon { font-size: 64px; margin-bottom: 16px; }
    .empty-state h3 { font-size: 20px; font-weight: 600; margin: 0; }
    .empty-state p  { color: #888; margin: 8px 0 16px; }
  `]
})
export class ProcessListPage implements OnInit {

  processes = signal<ProcessSummary[]>([]);
  isLoading = signal(true);
  activeFilter = 'all';

  constructor(
    private processService: ProcessService,
    private toast:          ToastService
  ) {
    addIcons({ add, personCircle, refreshOutline, documentTextOutline });
  }

  ngOnInit(): void { this.load(); }

  load(event?: any): void {
    this.isLoading.set(!event);
    const params = this.activeFilter === 'all' ? {}
        : { active: this.activeFilter === 'true' };

    this.processService.list(params).subscribe({
      next: resp => {
        this.processes.set(resp.data?.content ?? []);
        event?.target.complete();
        this.isLoading.set(false);
      },
      error: () => {
        event?.target.complete();
        this.isLoading.set(false);
        this.toast.error('Erro ao carregar processos.');
      }
    });
  }

  onFilterChange(): void { this.load(); }

  statusColor(status: string): string {
    return { PENDING: 'warning', OK: 'success', ERROR: 'danger', BLOCKED: 'dark' }[status] ?? 'medium';
  }

  statusLabel(status: string): string {
    return { PENDING: 'Pendente', OK: 'Ativo', ERROR: 'Erro', BLOCKED: 'Bloqueado' }[status] ?? status;
  }
}
import { Component, OnInit, signal } from '@angular/core';
import { CommonModule }              from '@angular/common';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton,
  IonList, IonItem, IonLabel, IonBadge, IonNote,
  IonInfiniteScroll, IonInfiniteScrollContent,
  IonRefresher, IonRefresherContent
} from '@ionic/angular/standalone';
import { NotificationService }    from '../../../services/notification.service';
import { NotificationHistoryItem } from '../../../models/notification.model';
import { LoadingSpinnerComponent }  from '../../shared/components/loading-spinner/loading-spinner.component';
import { EmptyStateComponent }      from '../../shared/components/empty-state/empty-state.component';
import { ToastService }             from '../../../core/services/toast.service';

@Component({
  selector:   'app-notification-history',
  standalone: true,
  imports: [
    CommonModule,
    IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton,
    IonList, IonItem, IonLabel, IonBadge, IonNote,
    IonInfiniteScroll, IonInfiniteScrollContent,
    IonRefresher, IonRefresherContent,
    LoadingSpinnerComponent, EmptyStateComponent
  ],
  template: `
    <ion-header>
      <ion-toolbar color="primary">
        <ion-back-button defaultHref="/profile" slot="start"></ion-back-button>
        <ion-title>Notificações</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content>
      <ion-refresher slot="fixed" (ionRefresh)="resetAndLoad($event)">
        <ion-refresher-content></ion-refresher-content>
      </ion-refresher>

      <app-loading-spinner *ngIf="isLoading()"></app-loading-spinner>

      <app-empty-state
        *ngIf="!isLoading() && items().length === 0"
        icon="notifications-off-outline"
        title="Sem notificações"
        message="Suas notificações de movimentações processuais aparecerão aqui.">
      </app-empty-state>

      <ion-list *ngIf="!isLoading() && items().length > 0">
        <ion-item *ngFor="let n of items()">
          <ion-label class="ion-text-wrap">
            <ion-note>{{ n.sentAt | date:'dd/MM/yyyy HH:mm' }}</ion-note>
            <p *ngIf="n.processNumber">Processo: {{ n.processNumber }}</p>
          </ion-label>
          <ion-badge slot="end" [color]="channelColor(n.channel)">
            {{ n.channel }}
          </ion-badge>
          <ion-badge slot="end" [color]="statusColor(n.status)" class="ion-margin-start">
            {{ statusLabel(n.status) }}
          </ion-badge>
        </ion-item>
      </ion-list>

      <ion-infinite-scroll [disabled]="!hasMore" (ionInfinite)="loadMore($event)">
        <ion-infinite-scroll-content loadingText="Carregando...">
        </ion-infinite-scroll-content>
      </ion-infinite-scroll>
    </ion-content>
  `
})
export class NotificationHistoryPage implements OnInit {

  items         = signal<NotificationHistoryItem[]>([]);
  isLoading     = signal(true);
  hasMore       = false;
  private page  = 0;

  constructor(
    private notifService: NotificationService,
    private toast:        ToastService
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.notifService.getHistory(this.page).subscribe({
      next: resp => {
        const content = resp.data?.content ?? [];
        this.items.update(i => [...i, ...content]);
        this.hasMore  = content.length === 20;
        this.isLoading.set(false);
      },
      error: () => { this.isLoading.set(false); this.toast.error('Erro ao carregar notificações.'); }
    });
  }

  loadMore(event: any): void {
    this.page++;
    this.notifService.getHistory(this.page).subscribe({
      next: resp => {
        this.items.update(i => [...i, ...(resp.data?.content ?? [])]);
        this.hasMore = (resp.data?.content?.length ?? 0) === 20;
        event.target.complete();
      },
      error: () => event.target.complete()
    });
  }

  resetAndLoad(event?: any): void {
    this.page = 0;
    this.items.set([]);
    this.notifService.getHistory(0).subscribe({
      next: resp => {
        this.items.set(resp.data?.content ?? []);
        this.hasMore = (resp.data?.content?.length ?? 0) === 20;
        event?.target.complete();
      },
      error: () => event?.target.complete()
    });
  }

  channelColor(ch: string) { return ch === 'EMAIL' ? 'primary' : 'tertiary'; }
  statusColor(s: string)   { return { SENT: 'success', FAILED: 'danger', SKIPPED: 'medium' }[s] ?? 'medium'; }
  statusLabel(s: string)   { return { SENT: 'Enviado', FAILED: 'Falhou', SKIPPED: 'Ignorado' }[s] ?? s; }
}
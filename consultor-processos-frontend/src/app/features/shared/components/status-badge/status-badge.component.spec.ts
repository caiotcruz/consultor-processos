import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IonicModule }               from '@ionic/angular';
import { StatusBadgeComponent }      from './status-badge.component';

describe('StatusBadgeComponent', () => {
  let component: StatusBadgeComponent;
  let fixture:   ComponentFixture<StatusBadgeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatusBadgeComponent, IonicModule.forRoot()]
    }).compileComponents();

    fixture   = TestBed.createComponent(StatusBadgeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('deve criar o componente', () => {
    expect(component).toBeTruthy();
  });

  it('deve ter status padrão PENDING', () => {
    expect(component.status).toBe('PENDING');
  });

  it('deve renderizar ion-badge', () => {
    const badge = fixture.nativeElement.querySelector('ion-badge');
    expect(badge).toBeTruthy();
  });

  it('deve exibir o label traduzido para OK', () => {
    component.status = 'OK';
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('ion-badge');
    expect(badge.textContent.trim()).toBe('Ativo');
  });

  it('deve exibir o label traduzido para ERROR', () => {
    component.status = 'ERROR';
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('ion-badge');
    expect(badge.textContent.trim()).toBe('Erro');
  });

  it('deve usar color success para OK', () => {
    component.status = 'OK';
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('ion-badge');
    expect((badge as HTMLIonBadgeElement).color).toBe('success');
  });

  it('deve usar color danger para ERROR', () => {
    component.status = 'ERROR';
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('ion-badge');
    expect((badge as HTMLIonBadgeElement).color).toBe('danger');
  });

  it('deve usar color warning para PENDING', () => {
    component.status = 'PENDING';
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('ion-badge');
    expect((badge as HTMLIonBadgeElement).color).toBe('warning');
  });
});
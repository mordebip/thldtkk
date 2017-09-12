import { Http, Headers, RequestOptions } from '@angular/http'
import { Injectable } from '@angular/core'
import { Observable } from 'rxjs'

import { environment as env} from '../../environments/environment'

import { GrowlMessageService } from '../services2/growl-message.service'
import { Quantity } from '../model2/quantity'

@Injectable()
export class QuantityService3 {

  constructor(
    private growlMessageService: GrowlMessageService,
    private http: Http
  ) { }

  getAll(): Observable<Quantity[]> {
    return this.http.get(env.contextPath + '/api/v3/quantities')
      .map(response => response.json() as Quantity[])
  }

  save(quantity: Quantity): Observable<Quantity> {
    const path: string = env.contextPath + '/api/v3/quantities'
    const headers = new Headers({ 'Content-Type': 'application/json;charset=UTF-8' })
    const options = new RequestOptions({ headers: headers })

    return this.http.post(path, quantity, options)
      .map(response => response.json() as Quantity)
      .do(quantity => {
        this.growlMessageService.buildAndShowMessage('success', 'operations.quantity.save.result.success')
      })
  }

}

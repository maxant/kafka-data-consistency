window.exports = window.exports || {}; // this allows us to add "export" to components, so they can be more easily transferred to real Angular apps later
var exports = window.exports;

const { Injectable } = ng.core;

@Injectable({
  providedIn: 'root',
})
export class MessageService {
  messages: string[] = [];

  add(message: string) {
    this.messages.push(message);
  }

  clear() {
    this.messages = [];
  }

  getMessages(): string[] {
      return this.messages;
  }
}

@Injectable({
  providedIn: 'root',
})
export class MyService {

    constructor(private messageService: MessageService) {

        console.log("constructor: " + messageService);
    }

  add(message: string) {
    this.messageService.add(message);
  }
  getMessages(): string[] {
    return this.messageService.getMessages();
  }


}


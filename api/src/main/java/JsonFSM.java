package org.agmip.data.json;

import java.util.ArrayDeque;
import java.util.Deque;

public class JsonFSM {

  // The past
  public enum State {
    FSM_STARTED,
    OBJECT_STARTED,
    WAITING_FOR_OBJECT_NAME,
    OBJECT_NAME_READ,
    WAITING_FOR_OBJECT_VALUE,
    OBJECT_VALUE_READ,
    ARRAY_STARTED,
    WAITING_FOR_ARRAY_ELEMENT,
    ARRAY_ELEMENT_READ,
    PLAIN_VALUE_READ,
    OBJECT_ENDED, // Only used for exception reporting
    ARRAY_ENDED, // Only used for exception reporting
  }

  // The verbs
  public enum Event {
    START_OBJECT,
    READ_OBJECT_SEPARATOR,
    WRITE_OBJECT_SEPARATOR,
    END_OBJECT,
    START_ARRAY,
    END_ARRAY,
    READ_VALUE_SEPARATOR,
    WRITE_VALUE_SEPARATOR,
    READ_STRING,
    WRITE_STRING,
    READ_VALUE,
    WRITE_VALUE,
  }

  protected Deque<State> stack = new ArrayDeque<>();
  protected Deque<Integer> arrayCounter = new ArrayDeque<>();

  public JsonFSM() {
    stack.add(State.FSM_STARTED);
  }

  private void swap(State newState) {
    stack.removeLast();
    stack.add(newState);
  }

  public State current() {
    return stack.peekLast();
  }

  public int depth() {
    int d = 0;
    for(State s : stack) {
      if (s == State.OBJECT_STARTED || s == State.ARRAY_STARTED) {
        d++;
      }
    }
    return d;
  }

  private void transitionToNested() {
    State current = this.current();
    switch (current) {
      case WAITING_FOR_OBJECT_VALUE:
        this.swap(State.OBJECT_VALUE_READ);
        break;
      case WAITING_FOR_ARRAY_ELEMENT:
        this.swap(State.ARRAY_ELEMENT_READ);
        break;
      case FSM_STARTED:
        stack.removeLast();
        break;
      default:
        throw new InvalidStateTransitionException(current, State.OBJECT_STARTED);
    }         
  }
  
  public void transition(Event event) {
    State current = this.current();
    State previous;
    switch (event) {
      case START_OBJECT:
        transitionToNested();
        stack.add(State.OBJECT_STARTED);
        stack.add(State.WAITING_FOR_OBJECT_NAME);
        break;
      case READ_OBJECT_SEPARATOR:
      case WRITE_OBJECT_SEPARATOR:
        if (current == State.OBJECT_NAME_READ) {
          this.swap(State.WAITING_FOR_OBJECT_VALUE);
        } else {
          throw new InvalidStateTransitionException(current, State.WAITING_FOR_OBJECT_VALUE);
        }
        break;
      case END_OBJECT:
        switch (current) {
          case OBJECT_VALUE_READ:
          case WAITING_FOR_OBJECT_NAME:
            previous = stack.removeLast();
            current = stack.removeLast();
            if (current != State.OBJECT_STARTED) {
              throw new InvalidStateTransitionException(previous, current);
            }
            break;
          default:
            throw new InvalidStateTransitionException(current, State.OBJECT_ENDED);
        }
        break;
      case START_ARRAY:
        transitionToNested();
        stack.add(State.ARRAY_STARTED);
        stack.add(State.WAITING_FOR_ARRAY_ELEMENT);
        arrayCounter.add(0);
        break;
      case END_ARRAY:
        previous = stack.removeLast();
        switch (previous) {
          case WAITING_FOR_ARRAY_ELEMENT:
            Integer counter = arrayCounter.removeLast();
            if (counter > 0) {
              throw new InvalidStateTransitionException("Cannot transition to ARRAY_ENDED because of extra array element separator (,)");
            }
          case ARRAY_ELEMENT_READ:
            current = stack.removeLast();
            if (current != State.ARRAY_STARTED) {
              throw new InvalidStateTransitionException(previous, current);
            }
            break;
          default:
            throw new InvalidStateTransitionException(previous, State.ARRAY_ENDED);
        }
        break;
      case READ_VALUE_SEPARATOR:
      case WRITE_VALUE_SEPARATOR:
        switch (current) {
          case OBJECT_VALUE_READ:
            this.swap(State.WAITING_FOR_OBJECT_NAME);
            break;
          case ARRAY_ELEMENT_READ:
            this.swap(State.WAITING_FOR_ARRAY_ELEMENT);
            break;
          default:
            throw new InvalidStateTransitionException("Cannot have a comma (,) while " + current);
        }
        break;
      case READ_STRING:
      case WRITE_STRING:
        switch (current) {
          case WAITING_FOR_OBJECT_NAME:
            this.swap(State.OBJECT_NAME_READ);
            break;
          case WAITING_FOR_OBJECT_VALUE:
            this.swap(State.OBJECT_VALUE_READ);
            break;
          case WAITING_FOR_ARRAY_ELEMENT:
            this.swap(State.ARRAY_ELEMENT_READ);
            Integer counter = arrayCounter.removeLast();
            counter++;
            arrayCounter.add(counter);
            break;
          case FSM_STARTED:
            this.swap(State.PLAIN_VALUE_READ);
            break;
          default:
            throw new InvalidStateTransitionException(current, event);
        }
        break;
      case READ_VALUE:
      case WRITE_VALUE:
        switch (current) {
          case WAITING_FOR_OBJECT_VALUE:
            this.swap(State.OBJECT_VALUE_READ);
            break;
          case WAITING_FOR_ARRAY_ELEMENT:
            this.swap(State.ARRAY_ELEMENT_READ);
            Integer counter = arrayCounter.removeLast();
            counter++;
            arrayCounter.add(counter);
            break;
          default:
            throw new InvalidStateTransitionException(current, event);
        }
        break;
      default:
        throw new UnhandledEventException(event);
    }
  }

  public static class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(JsonFSM.State current, JsonFSM.State invalid) {
      super("Cannot transition to " + invalid + " from " + current);
    }

    public InvalidStateTransitionException(JsonFSM.State current, Event event) {
      super("Cannot " + event + " from " + current );
    }

    public InvalidStateTransitionException(String message) {
      super(message);
    }
  }

  public static class UnhandledEventException extends RuntimeException {
    public UnhandledEventException(Event event) {
      super("Event " + event + " is unhandled.");
    }
  }
}

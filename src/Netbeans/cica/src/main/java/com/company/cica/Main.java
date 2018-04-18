/*
 * The MIT License
 *
 * Copyright 2018 jakob.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.company.cica;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

/**
 *
 * @author jakob
 */
public class Main {
    public static class PenDownAtEvent {
        public int x;
        public int y;

        public PenDownAtEvent(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
    
    public static class PenMovedToEvent {
        public int x;
        public int y;

        public PenMovedToEvent(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
    
    public static class PenUpEvent {
        
    }
    
    private static class Patterns {
        public static Pattern line() {
            return new Pattern() {
                @Override
                public Recognizer recognize() {
                    return new StateBasedRecognizer(new RecognizerState() {
                        @Override
                        public RecognizerState nextOrNullAfter(Object event) {
                            if(event instanceof PenDownAtEvent) {
                                return afterPenDown((PenDownAtEvent)event);
                            } else {
                                return null;
                            }
                        }

                        private RecognizerState afterPenDown(PenDownAtEvent penDownEvent) {
                            final int x = penDownEvent.x;
                            final int y = penDownEvent.y;
                            
                            return new RecognizerState() {
                                @Override
                                public RecognizerState nextOrNullAfter(Object event) {
                                    if(event instanceof PenMovedToEvent) {
                                        return firstDirection(x, y, (PenMovedToEvent)event);
                                    } else {
                                        return null;
                                    }
                                }
                            };
                        }

                        private RecognizerState firstDirection(int x1, int y1, PenMovedToEvent penMovedToEvent) {
                            final int x2 = penMovedToEvent.x;
                            final int y2 = penMovedToEvent.y;
                            
                            //int distance = (int)Math.hypot(x1-x2, y1-y2);
                            final double direction = Math.toDegrees(Math.atan2(x2 - x1, y2 - y1));
                            
                            return new RecognizerState() {
                                @Override
                                public RecognizerState nextOrNullAfter(Object event) {
                                    if(event instanceof PenMovedToEvent) {
                                        return proceedingDirection(x2, y2, direction, (PenMovedToEvent)event);
                                    } else if(event instanceof PenUpEvent) {
                                        return new RecognizerState() {
                                            @Override
                                            public RecognizerState nextOrNullAfter(Object event) {
                                                return null;
                                            }
                                        };
                                    } else {
                                        return null;
                                    }
                                }
                            };
                        }

                        private RecognizerState proceedingDirection(int x1, int y1, double referenceDirection, PenMovedToEvent penMovedToEvent) {
                            final int x2 = penMovedToEvent.x;
                            final int y2 = penMovedToEvent.y;
                            
                            //int distance = (int)Math.hypot(x1-x2, y1-y2);
                            final double direction = Math.toDegrees(Math.atan2(x2 - x1, y2 - y1));
                            if(Math.abs(referenceDirection - direction) <= 10) {
                                return new RecognizerState() {
                                    @Override
                                    public RecognizerState nextOrNullAfter(Object event) {
                                        if(event instanceof PenMovedToEvent) {
                                            return proceedingDirection(x2, y2, direction, (PenMovedToEvent)event);
                                        } else if(event instanceof PenUpEvent) {
                                            return new RecognizerState() {
                                                @Override
                                                public RecognizerState nextOrNullAfter(Object event) {
                                                    return null;
                                                }
                                            };
                                        } else {
                                            return null;
                                        }
                                    }
                                };
                            } else {
                                return null;
                            }
                        }
                    });
                }
            };
        }
    }
    
    public static void main(String[] args) {
        Pattern p = Patterns.line();
        
        Recognizer r = p.recognize();
        
        Object[] events = {
            new PenDownAtEvent(5, 5),
            new PenMovedToEvent(10, 10),
            new PenMovedToEvent(16, 15),
            new PenUpEvent()
        };
        
        Queue eventQueue = new ArrayDeque(Arrays.asList(events));
        
        while(eventQueue.size() > 0) {
            Object event = eventQueue.poll();
            if(!r.accepts(event)) {
                break;
            }
        }
        
        if(eventQueue.isEmpty()) {
            System.out.println("Drawing is a line");
        } else {
            System.out.println("Drawing is not a line");
        }
    }
}

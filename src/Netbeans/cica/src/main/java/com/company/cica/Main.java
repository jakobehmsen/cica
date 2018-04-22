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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.JPanel;

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

        @Override
        public String toString() {
            return "PenDownAtEvent{" + "x=" + x + ", y=" + y + '}';
        }
    }
    
    public static class PenMovedToEvent {
        public int x;
        public int y;

        public PenMovedToEvent(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "PenMovedToEvent{" + "x=" + x + ", y=" + y + '}';
        }
    }
    
    public static class PenUpEvent {
        @Override
        public String toString() {
            return "PenUpEvent{" + '}';
        }
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
                            return proceedingDirection(x1, y1, x1, y1, false, 0.0, penMovedToEvent);
                        }

                        private RecognizerState proceedingDirection(int xStart, int yStart, int x1, int y1, boolean hasReferenceDirection, double referenceDirection, PenMovedToEvent penMovedToEvent) {
                            final int x2 = penMovedToEvent.x;
                            final int y2 = penMovedToEvent.y;
                            
                            int distance = (int)Math.hypot(x1-x2, y1-y2);
                            //System.out.println("distance=" + distance);
                            if(distance > 5) {
                                //System.out.println("accepted distance");
                                final double direction = Math.toDegrees(Math.atan2(x2 - x1, y2 - y1));
                                //System.out.println("referenceDirection=" + referenceDirection);
                                //System.out.println("direction=" + direction);
                                if(!hasReferenceDirection || Math.abs(referenceDirection - direction) <= 20) {
                                    return proceedingDirectionState(xStart, yStart, x2, y2, true, direction);
                                } else {
                                    return null;
                                }
                            } else {
                                //System.out.println("rejected distance");
                                return proceedingDirectionState(xStart, yStart, x1, y1, hasReferenceDirection, referenceDirection);
                            }
                        }
                        
                        private RecognizerState proceedingDirectionState(int xStart, int yStart, int x1, int y1, boolean hasReferenceDirection, double referenceDirection) {
                            return new RecognizerState() {
                                @Override
                                public RecognizerState nextOrNullAfter(Object event) {
                                    if(event instanceof PenMovedToEvent) {
                                        return proceedingDirection(xStart, yStart, x1, y1, hasReferenceDirection, referenceDirection, (PenMovedToEvent)event);
                                    } else if(event instanceof PenUpEvent) {
                                        return new RecognizerState() {
                                            @Override
                                            public RecognizerState nextOrNullAfter(Object event) {
                                                return null;
                                            }

                                            @Override
                                            public CanvasAction getIntentOrNull() {
                                                return new CanvasAction() {
                                                    @Override
                                                    public void perform(Canvas canvas) {
                                                        Drawing drawing = canvas.newDrawing(xStart, yStart);
                                                        drawing.moveTo(x1, y1);
                                                    }
                                                };
                                            }
                                        };
                                    } else {
                                        return null;
                                    }
                                }
                            };
                        }
                    });
                }
            };
        }
    }
    
    private static class CanvasPanel extends JPanel implements Canvas {
        private class DrawingPanel implements Drawing {
            private GeneralPath path = new GeneralPath();
            
            public DrawingPanel(int x, int y) {
                path.moveTo(x, y);
            }
            
            @Override
            public void moveTo(int x, int y) {
                path.lineTo(x, y);
            }

            @Override
            public void delete() {
                drawings.remove(this);
            }
            
            public void drawOn(Graphics2D graphics) {
                graphics.draw(path);
            }
        }
        
        private List<DrawingPanel> drawings = new ArrayList<>();
        
        @Override
        public Drawing newDrawing(int x, int y) {
            DrawingPanel drawing = new DrawingPanel(x, y);
            
            drawings.add(drawing);
            
            return drawing;
        }

        @Override
        public void paint(Graphics g) {
            g.setColor(getBackground());
            g.fillRect(getX(), getY(), getWidth(), getHeight());
            g.setColor(getForeground());
            drawings.forEach(d -> d.drawOn((Graphics2D) g));
        }
    }
    
    public static void main(String[] args) {        
        Pattern p = Patterns.line();
        
        BlockingQueue<Object> eventQueue = new ArrayBlockingQueue<>(10);
        
        CanvasPanel canvasPanel = new CanvasPanel();
        Canvas canvas = canvasPanel;
        
        Thread eventProcessor = new Thread(new Runnable() {
            Recognizer recognizer;
            Drawing drawing;
            
            @Override
            public void run() {
                try {
                    while(true) {
                        Object event = eventQueue.take();

                        if(event instanceof PenDownAtEvent) {
                            // Start recognition
                            recognizer = p.recognize();
                            
                            System.out.println("Start recognition");
                            
                            drawing = canvas.newDrawing(((PenDownAtEvent)event).x, ((PenDownAtEvent)event).y);
                        }
                        
                        if(recognizer != null) {                        
                            //System.out.println("event=" + event);
                            
                            if(event instanceof PenMovedToEvent) {
                                drawing.moveTo(((PenMovedToEvent)event).x, ((PenMovedToEvent)event).y);
                            }
                        
                            if(!recognizer.accepts(event)) {
                                // Recognition failed
                                recognizer = null;
                                
                                System.out.println("Recognition failed");
                                
                                drawing.delete();
                            } else {
                                if(event instanceof PenUpEvent) {
                                    // Recognition succeded
                                
                                    System.out.println("Recognition succeded");
                                    
                                    drawing.delete();
                                    
                                    CanvasAction intent = recognizer.getIntentOrNull();
                                    intent.perform(canvas);
                                    recognizer = null;
                                }
                            }
                        }
                        
                        canvasPanel.repaint();
                        canvasPanel.invalidate();
                    }
                } catch (InterruptedException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
        
        eventProcessor.start();
        
        JFrame frame = new JFrame("Line recognizer");
        frame.setContentPane(canvasPanel);
        
        canvasPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                eventQueue.offer(new PenDownAtEvent(e.getX(), e.getY()));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                eventQueue.offer(new PenUpEvent());
            }
        });
        
        canvasPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                eventQueue.offer(new PenMovedToEvent(e.getX(), e.getY()));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                eventQueue.offer(new PenMovedToEvent(e.getX(), e.getY()));
            }
        });
        
        frame.setSize(640, 480);
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                eventProcessor.interrupt();
            }
        });
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}

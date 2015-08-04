package com.bytezone.dm3270.display;

import java.util.ArrayList;
import java.util.List;

import com.bytezone.dm3270.application.Utility;
import com.bytezone.dm3270.attributes.Attribute;
import com.bytezone.dm3270.attributes.StartFieldAttribute;
import com.bytezone.dm3270.orders.Order;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public final class ScreenPosition
{
  private static String[] charString = new String[256];

  // GraphicsEscape characters
  private static final byte TOP_LEFT = (byte) 0xC5;
  private static final byte TOP_RIGHT = (byte) 0xD5;
  private static final byte BOTTOM_LEFT = (byte) 0xC4;
  private static final byte BOTTOM_RIGHT = (byte) 0xD4;
  private static final byte HORIZONTAL_LINE = (byte) 0xA2;
  private static final byte VERTICAL_LINE = (byte) 0x85;

  private StartFieldAttribute startFieldAttribute;
  private final List<Attribute> attributes = new ArrayList<> ();

  public final int position;
  private byte value;
  private boolean isGraphics;
  private boolean isVisible = true;
  private ScreenContext screenContext;
  private final ScreenContext baseContext;

  private final CharacterSize characterSize;
  private final GraphicsContext gc;

  static
  {
    // build strings to use in the screen-drawing routine
    for (int i = 0; i < 33; i++)
      charString[i] = " ";
    for (int i = 33; i < 256; i++)
      charString[i] = (char) i + "";
  }

  public ScreenPosition (int position, GraphicsContext gc, CharacterSize characterSize,
      ScreenContext base)
  {
    this.position = position;
    this.gc = gc;
    this.characterSize = characterSize;
    baseContext = base;
    reset ();
  }

  public StartFieldAttribute getStartFieldAttribute ()
  {
    return startFieldAttribute;
  }

  public void setStartField (StartFieldAttribute startFieldAttribute)
  {
    this.startFieldAttribute = startFieldAttribute;
  }

  public void addAttribute (Attribute attribute)
  {
    attributes.add (attribute);
  }

  public List<Attribute> getAttributes ()
  {
    return attributes;
  }

  // called by Field when deleting a character
  public void clearAttributes ()
  {
    attributes.clear ();
  }

  public void reset ()
  {
    isVisible = true;
    value = 0;
    isGraphics = false;
    startFieldAttribute = null;
    attributes.clear ();
    screenContext = baseContext;
  }

  // Password fields etc
  public void setVisible (boolean visible)
  {
    this.isVisible = visible;
  }

  // All the colour and highlight options
  public void setScreenContext (ScreenContext screenContext)
  {
    this.screenContext = screenContext;
  }

  public ScreenContext getScreenContext ()
  {
    return screenContext;
  }

  public boolean isStartField ()
  {
    return startFieldAttribute != null;
  }

  public boolean isGraphicsChar ()
  {
    return isGraphics;
  }

  public void setChar (byte value)
  {
    this.value = value;
    isGraphics = false;
  }

  public void setGraphicsChar (byte value)
  {
    this.value = value;
    isGraphics = true;
  }

  // only used by other classes
  public char getChar ()
  {
    if (isStartField () || (value <= 32 && value >= 0))
      return ' ';

    if (isGraphics)
      switch (value)
      {
        case HORIZONTAL_LINE:
          return '-';
        case VERTICAL_LINE:
          return '|';
        default:
          return '*';
      }

    return (char) Utility.ebc2asc[value & 0xFF];
  }

  public String getCharString ()
  {
    if (isStartField ())
      return " ";

    if (isGraphics)
      switch (value)
      {
        case HORIZONTAL_LINE:
          return "-";
        case VERTICAL_LINE:
          return "|";
        default:
          return "*";
      }

    return charString[Utility.ebc2asc[value & 0xFF]];
  }

  public byte getByte ()
  {
    return value;
  }

  public boolean isNull ()
  {
    return value == 0;
  }

  public int pack (byte[] buffer, int ptr, byte order)
  {
    assert isStartField ();

    buffer[ptr++] = order;

    if (order == Order.START_FIELD)
      buffer[ptr++] = startFieldAttribute.getAttributeValue ();
    else if (order == Order.START_FIELD_EXTENDED)
    {
      buffer[ptr++] = (byte) (attributes.size () + 1);// includes the SFA
      ptr = startFieldAttribute.pack (buffer, ptr);
      for (Attribute attribute : attributes)
        ptr = attribute.pack (buffer, ptr);
    }
    else
      System.out.println ("I should throw an exception here");

    return ptr;
  }

  public int pack (byte[] buffer, int ptr, byte[] replyTypes)
  {
    assert!isStartField ();

    for (Attribute attribute : attributes)
      if (attribute.matches (Attribute.XA_RESET) || attribute.matches (replyTypes))
      {
        buffer[ptr++] = Order.SET_ATTRIBUTE;
        ptr = attribute.pack (buffer, ptr);// packs type/value pair
      }

    buffer[ptr++] = value;

    return ptr;
  }

  public void draw (int x, int y, boolean hasCursor)
  {
    int charWidth = characterSize.getWidth ();
    int charHeight = characterSize.getHeight ();
    int ascent = characterSize.getAscent ();
    int descent = characterSize.getDescent ();

    Color foregroundColor = null;
    Color backgroundColor = null;

    gc.translate (0.5, 0.5);// move coordinate grid to use the center of pixels

    // Draw background
    if (isVisible)
    {
      if (hasCursor)
        if (screenContext.reverseVideo)
        {
          backgroundColor = screenContext.backgroundColor;
          foregroundColor = screenContext.foregroundColor;
        }
        else
        {
          backgroundColor = screenContext.foregroundColor;
          foregroundColor = screenContext.backgroundColor;
        }
      else if (screenContext.reverseVideo)
      {
        backgroundColor = screenContext.foregroundColor;
        foregroundColor = screenContext.backgroundColor;
      }
      else
      {
        backgroundColor = screenContext.backgroundColor;
        foregroundColor = screenContext.foregroundColor;
      }
    }
    else if (hasCursor)
      backgroundColor = screenContext.foregroundColor;
    else
      backgroundColor = screenContext.backgroundColor;

    gc.setFill (backgroundColor);

    // without the offset Windows will leave ghosting behind (even though we have
    // translated the screen coordinates)
    if (hasCursor)
      gc.fillRect (x + 0.5, y + 0.5, charWidth, ascent + descent);
    else
      gc.fillRect (x + 0.5, y + 0.5, charWidth, charHeight);

    if (screenContext.highIntensity)
    {
      if (foregroundColor == Color.WHITESMOKE)
        foregroundColor = Color.WHITE;
    }

    // Draw foreground
    if (isVisible)
      if (isGraphics)
        doGraphics (foregroundColor, backgroundColor, hasCursor, x, y);
      else
      {
        gc.setFill (foregroundColor);
        gc.fillText (getCharString (), x, y + ascent);

        if (screenContext.underscore)
        {
          gc.setStroke (screenContext.foregroundColor);
          double y2 = y + ascent + descent + 1;
          gc.strokeLine (x + 1, y2, x + charWidth, y2);
        }
      }
    gc.translate (-0.5, -0.5);// restore coordinate grid
  }

  private void doGraphics (Color foregroundColor, Color backgroundColor,
      boolean hasCursor, int x, int y)
  {
    int width = characterSize.getWidth ();
    int height = characterSize.getHeight ();
    int dx = width / 2;
    int dy = height / 2;

    gc.setStroke (foregroundColor);

    switch (value)
    {
      case HORIZONTAL_LINE:
        gc.strokeLine (x, y + dy, x + width, y + dy);
        break;

      case VERTICAL_LINE:
        gc.strokeLine (x + dx, y, x + dx, y + height);
        break;

      case TOP_LEFT:
        gc.strokeLine (x + dx, y + dy, x + dx, y + height);// vertical
        gc.strokeLine (x + dx, y + dy, x + width, y + dy);// horizontal
        break;

      case TOP_RIGHT:
        gc.strokeLine (x + dx, y + dy, x + dx, y + height);// vertical
        gc.strokeLine (x, y + dy, x + dx, y + dy);// horizontal
        break;

      case BOTTOM_LEFT:
        gc.strokeLine (x + dx, y, x + dx, y + dy);// vertical
        gc.strokeLine (x + dx, y + dy, x + width, y + dy);// horizontal
        break;

      case BOTTOM_RIGHT:
        gc.strokeLine (x + dx, y, x + dx, y + dy);// vertical
        gc.strokeLine (x, y + dy, x + dx, y + dy);// horizontal
        break;

      default:
        gc.fillText (getCharString (), x, y + characterSize.getAscent ());
        System.out.printf ("Unknown graphics character: %02X%n", value);
    }

    if (hasCursor && (value == VERTICAL_LINE || value == TOP_LEFT || value == TOP_RIGHT))
    {
      gc.setStroke (backgroundColor);
      dy = y + characterSize.getAscent () + characterSize.getDescent () + 1;
      gc.strokeLine (x + dx, dy, x + dx, y + height);
    }
  }

  @Override
  public String toString ()
  {
    StringBuilder text = new StringBuilder ();
    //    text.append (String.format ("%4d %-20s", position, screenContext));
    if (isStartField ())
    {
      text.append ("  ");
      text.append (startFieldAttribute);
    }
    else
    {
      for (Attribute attribute : attributes)
        text.append ("  " + attribute);
    }
    text.append (", byte: " + getCharString ());

    return text.toString ();
  }
}
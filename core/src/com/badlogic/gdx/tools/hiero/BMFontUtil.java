/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.tools.hiero;

import java.awt.Font;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import com.badlogic.gdx.scenes.scene2d.ui.Value;
import com.badlogic.gdx.tools.hiero.unicodefont.effects.ConfigurableEffect;
import com.badlogic.gdx.utils.IntIntMap;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import sun.security.action.GetLongAction;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.tools.hiero.unicodefont.Glyph;
import com.badlogic.gdx.tools.hiero.unicodefont.GlyphPage;
import com.badlogic.gdx.tools.hiero.unicodefont.UnicodeFont;

/** @author Nathan Sweet */
public class BMFontUtil {
	private final UnicodeFont unicodeFont;

	public BMFontUtil (UnicodeFont unicodeFont) {
		this.unicodeFont = unicodeFont;
	}

	public void save (File outputBMFontFile) throws IOException {
		File outputDir = outputBMFontFile.getParentFile();
		String outputName = outputBMFontFile.getName();
		if (outputName.endsWith(".fnt")) outputName = outputName.substring(0, outputName.length() - 4);

		unicodeFont.setGlyphPageHeight(1024);
		
		unicodeFont.loadGlyphs();

		// This is a tweak to limit texture height to minimum
		GlyphPage p = (GlyphPage) unicodeFont.getGlyphPages().get(0);
		int realHeight = p.getPageY() + unicodeFont.getLineHeight()+5; // keep space for shadow
		
		Gdx.app.log("BMFontUtil", "Real texture height: " + realHeight);
		
		PrintStream out = new PrintStream(new FileOutputStream(new File(outputDir, outputName + ".fnt")));
		Font font = unicodeFont.getFont();
		int pageWidth = unicodeFont.getGlyphPageWidth();
		int pageHeight = unicodeFont.getGlyphPageHeight();
		out.println("info face=\"" + font.getFontName() + "\" size=" + font.getSize() + " bold=" + (font.isBold() ? 1 : 0)
			+ " italic=" + (font.isItalic() ? 1 : 0) + " charset=\"\" unicode=0 stretchH=100 smooth=1 aa=1 padding="
			+ unicodeFont.getPaddingTop() + "," + unicodeFont.getPaddingLeft() + "," + unicodeFont.getPaddingBottom() + ","
			+ unicodeFont.getPaddingRight() + " spacing=" + unicodeFont.getPaddingAdvanceX() + ","
			+ unicodeFont.getPaddingAdvanceY());
		out.println("common lineHeight=" + unicodeFont.getLineHeight() + " base=" + unicodeFont.getAscent() + " scaleW="
			+ pageWidth + " scaleH=" + pageHeight + " pages=" + unicodeFont.getGlyphPages().size() + " packed=0");

		int pageIndex = 0, glyphCount = 0;
		for (Iterator pageIter = unicodeFont.getGlyphPages().iterator(); pageIter.hasNext();) {
			GlyphPage page = (GlyphPage)pageIter.next();
			String fileName;
			if (pageIndex == 0 && !pageIter.hasNext())
				fileName = outputName + ".png";
			else
				fileName = outputName + (pageIndex + 1) + ".png";
			out.println("page id=" + pageIndex + " file=\"" + fileName + "\"");
			glyphCount += page.getGlyphs().size();
			pageIndex++;
		}

		out.println("chars count=" + glyphCount);

		// Always output space entry (codepoint 32).
		int[] glyphMetrics = getGlyphMetrics(font, 32);
		int xAdvance = glyphMetrics[1];
		out.println("char id=32   x=0     y=0     width=0     height=0     xoffset=0     yoffset=" + unicodeFont.getAscent()
			+ "    xadvance=" + xAdvance + "     page=0  chnl=0 ");

		pageIndex = 0;
		List allGlyphs = new ArrayList(512);
		for (Iterator pageIter = unicodeFont.getGlyphPages().iterator(); pageIter.hasNext();) {
			GlyphPage page = (GlyphPage)pageIter.next();
			for (Iterator glyphIter = page.getGlyphs().iterator(); glyphIter.hasNext();) {
				Glyph glyph = (Glyph)glyphIter.next();

				glyphMetrics = getGlyphMetrics(font, glyph.getCodePoint());
				int xOffset = glyphMetrics[0];
				xAdvance = glyphMetrics[1];

				out.println("char id=" + glyph.getCodePoint() + "   " + "x=" + (int)(glyph.getU() * pageWidth) + "     y="
					+ (int)(glyph.getV() * pageHeight) + "     width=" + glyph.getWidth() + "     height=" + glyph.getHeight()
					+ "     xoffset=" + xOffset + "     yoffset=" + glyph.getYOffset() + "    xadvance=" + xAdvance + "     page="
					+ pageIndex + "  chnl=0 ");
			}
			allGlyphs.addAll(page.getGlyphs());
			pageIndex++;
		}

		String ttfFileRef = unicodeFont.getFontFile();
		if (ttfFileRef == null)
			System.out.println("Kerning information could not be output because a TTF font file was not specified.");
		else {
			Kerning kerning = new Kerning();
			try {
				kerning.load(Gdx.files.internal(ttfFileRef).read(), font.getSize());
			} catch (IOException ex) {
				System.out.println("Unable to read kerning information from font: " + ttfFileRef);
			}

			Map glyphCodeToCodePoint = new HashMap();
			for (Iterator iter = allGlyphs.iterator(); iter.hasNext();) {
				Glyph glyph = (Glyph)iter.next();
				glyphCodeToCodePoint.put(new Integer(getGlyphCode(font, glyph.getCodePoint())), new Integer(glyph.getCodePoint()));
			}

			List kernings = new ArrayList(256);
			class KerningPair {
				public int firstCodePoint, secondCodePoint, offset;
			}
            int kerningCount = 0;
			for (Iterator iter1 = allGlyphs.iterator(); iter1.hasNext();) {
				Glyph firstGlyph = (Glyph)iter1.next();
				int firstGlyphCode = getGlyphCode(font, firstGlyph.getCodePoint());
				IntIntMap.Values values = kerning.getKernings().values(); //kerning.getValues(firstGlyphCode);
				if (values == null) continue;
                while(values.hasNext()){
                    kerningCount++;
                    int value = values.next();
                    Integer secondCodePoint = (Integer)glyphCodeToCodePoint.get(new Integer(value & 0xffff));
                    if (secondCodePoint == null) continue; // We may not be outputting the second character.
                    int offset = value >> 16;
                    KerningPair pair = new KerningPair();
                    pair.firstCodePoint = firstGlyph.getCodePoint();
                    pair.secondCodePoint = secondCodePoint.intValue();
                    pair.offset = offset;
                    kernings.add(pair);
                }
			}
			out.println("kernings count=" + kerningCount);
			for (Iterator iter = kernings.iterator(); iter.hasNext();) {
				KerningPair pair = (KerningPair)iter.next();
				out.println("kerning first=" + pair.firstCodePoint + "  second=" + pair.secondCodePoint + "  amount=" + pair.offset);
			}
		}
		out.close();

		int width = unicodeFont.getGlyphPageWidth();
		int height = unicodeFont.getGlyphPageHeight();
		IntBuffer buffer = BufferUtils.createIntBuffer(width * height);
		BufferedImage pageImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		int[] row = new int[width];

		pageIndex = 0;
		for (Iterator pageIter = unicodeFont.getGlyphPages().iterator(); pageIter.hasNext();) {
			GlyphPage page = (GlyphPage)pageIter.next();
			String fileName;
			if (pageIndex == 0 && !pageIter.hasNext())
				fileName = outputName + ".png";
			else
				fileName = outputName + (pageIndex + 1) + ".png";

			page.getTexture().bind();
			buffer.clear();
			GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, buffer);
			WritableRaster raster = pageImage.getRaster();
			for (int y = 0; y < height; y++) {
				buffer.get(row);
				raster.setDataElements(0, y, width, 1, row);
			}
			File imageOutputFile = new File(outputDir, fileName);
			
			if (realHeight < height) {
				ImageIO.write(pageImage.getSubimage(0, 0, width, realHeight), "png", imageOutputFile);
			} else {
				ImageIO.write(pageImage, "png", imageOutputFile);
			}
			pageIndex++;
		}
	}

	private int getGlyphCode (Font font, int codePoint) {
		char[] chars = Character.toChars(codePoint);
		GlyphVector vector = font.layoutGlyphVector(GlyphPage.renderContext, chars, 0, chars.length, Font.LAYOUT_LEFT_TO_RIGHT);
		return vector.getGlyphCode(0);
	}

	private int[] getGlyphMetrics (Font font, int codePoint) {
		// xOffset and xAdvance will be incorrect for unicode characters such as combining marks or non-spacing characters
		// (eg Pnujabi's "\u0A1C\u0A47") that require the context of surrounding glyphs to determine spacing, but thisis the
		// best we can do with the BMFont format.
		char[] chars = Character.toChars(codePoint);
		GlyphVector vector = font.layoutGlyphVector(GlyphPage.renderContext, chars, 0, chars.length, Font.LAYOUT_LEFT_TO_RIGHT);
		GlyphMetrics metrics = vector.getGlyphMetrics(0);
		int xOffset = vector.getGlyphPixelBounds(0, GlyphPage.renderContext, 0.5f, 0).x - unicodeFont.getPaddingLeft();
		int xAdvance = (int)(metrics.getAdvanceX() + unicodeFont.getPaddingAdvanceX() + unicodeFont.getPaddingLeft() + unicodeFont
			.getPaddingRight());
		return new int[] {xOffset, xAdvance};
	}
}

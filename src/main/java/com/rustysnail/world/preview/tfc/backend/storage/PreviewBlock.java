package com.rustysnail.world.preview.tfc.backend.storage;

import java.io.Serial;
import java.io.Serializable;
import com.rustysnail.world.preview.tfc.RenderSettings;
import com.rustysnail.world.preview.tfc.WorldPreview;

public class PreviewBlock implements Serializable
{
    @Serial
    private static final long serialVersionUID = -6140310220242894115L;

    static int sectionQuartStride(long flags, int visualQuartStride)
    {
        return flags == RenderSettings.RenderMode.TFC_CROP_SUITABILITY.flag ? 1 : visualQuartStride;
    }

    private final long flags;
    private final PreviewSection[] sections = new PreviewSection[1024];

    public PreviewBlock(long flags)
    {
        this.flags = flags;
    }

    public synchronized PreviewSection get(int quartX, int quartZ)
    {
        int idx = (quartX >> 6 & 31) * 32 + (quartZ >> 6 & 31);
        PreviewSection section = this.sections[idx];
        if (section == null)
        {
            section = this.sections[idx] = this.sectionFactory(quartX, quartZ);
        }

        return section;
    }

    private PreviewSection sectionFactory(int quartX, int quartZ)
    {
        if (this.flags == 1L)
        {
            return new PreviewSectionStructure(quartX, quartZ);
        }
        else
        {
            // Crop suitability is always generated and stored at true quart resolution, independent
            // of the current visual sampler/zoom. Other flags retain the existing global stride.
            int quartStride = sectionQuartStride(this.flags, WorldPreview.get().renderSettings().quartStride());
            if (WorldPreview.get().cfg().enableCompression)
            {
                return (switch (quartStride)
                {
                    case 1 -> new PreviewSectionCompressed.Full(quartX, quartZ);
                    case 2 -> new PreviewSectionCompressed.Half(quartX, quartZ);
                    case 4 -> new PreviewSectionCompressed.Quarter(quartX, quartZ);
                    default -> throw new IllegalStateException("Unexpected quartStride value: " + quartStride);
                });
            }
            else
            {
                return (switch (quartStride)
                {
                    case 1 -> new PreviewSectionFull(quartX, quartZ);
                    case 2 -> new PreviewSectionHalf(quartX, quartZ);
                    case 4 -> new PreviewSectionQuarter(quartX, quartZ);
                    default -> throw new IllegalStateException("Unexpected quartStride value: " + quartStride);
                });
            }
        }
    }
}

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.R
import com.example.RealmsAI.models.PoseSlot

class PoseDisplayAdapter(
    private val poses: List<PoseSlot>
) : RecyclerView.Adapter<PoseDisplayAdapter.PoseViewHolder>() {

    class PoseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val poseImage: ImageView = view.findViewById(R.id.poseImage)
        val poseName: TextView = view.findViewById(R.id.poseName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PoseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.pose_display, parent, false)
        return PoseViewHolder(view)
    }

    override fun onBindViewHolder(holder: PoseViewHolder, position: Int) {
        val pose = poses[position]
        holder.poseName.text = pose.name
        Glide.with(holder.poseImage.context)
            .load(pose.uri)
            .placeholder(R.drawable.silhouette)
            .into(holder.poseImage)
    }

    override fun getItemCount() = poses.size
}
